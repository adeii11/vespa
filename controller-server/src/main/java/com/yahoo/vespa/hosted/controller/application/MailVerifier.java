// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.LockedTenant;
import com.yahoo.vespa.hosted.controller.TenantController;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Mail;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Mailer;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.tenant.TenantContacts;
import com.yahoo.vespa.hosted.controller.tenant.TenantInfo;
import com.yahoo.vespa.hosted.controller.tenant.PendingMailVerification;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.yahoo.yolean.Exceptions.uncheck;


/**
 * @author olaa
 */
public class MailVerifier {

    private static final Duration VERIFICATION_DEADLINE = Duration.ofDays(7);

    private final TenantController tenantController;
    private final Mailer mailer;
    private final CuratorDb curatorDb;
    private final Clock clock;
    private final URI dashboardUri;

    public MailVerifier(URI dashboardUri, TenantController tenantController, Mailer mailer, CuratorDb curatorDb, Clock clock) {
        this.tenantController = tenantController;
        this.mailer = mailer;
        this.curatorDb = curatorDb;
        this.clock = clock;
        this.dashboardUri = dashboardUri;
    }

    public PendingMailVerification sendMailVerification(TenantName tenantName, String email, PendingMailVerification.MailType mailType) {
        if (!email.contains("@")) {
            throw new IllegalArgumentException("Invalid email address");
        }

        var verificationCode = UUID.randomUUID().toString();
        var verificationDeadline = clock.instant().plus(VERIFICATION_DEADLINE);
        var pendingMailVerification = new PendingMailVerification(tenantName, email, verificationCode, verificationDeadline, mailType);
        writePendingVerification(pendingMailVerification);
        mailer.send(mailOf(pendingMailVerification));
        return pendingMailVerification;
    }

    public Optional<PendingMailVerification> resendMailVerification(TenantName tenantName, String email, PendingMailVerification.MailType mailType) {
        var oldPendingVerification = curatorDb.listPendingMailVerifications()
                .stream()
                .filter(pendingMailVerification ->
                                pendingMailVerification.getMailAddress().equals(email) &&
                                        pendingMailVerification.getMailType().equals(mailType) &&
                                        pendingMailVerification.getTenantName().equals(tenantName)
                ).findFirst();

        if (oldPendingVerification.isEmpty())
            return Optional.empty();

        try (var lock = curatorDb.lockPendingMailVerification(oldPendingVerification.get().getVerificationCode())) {
            curatorDb.deletePendingMailVerification(oldPendingVerification.get());
        }

        return Optional.of(sendMailVerification(tenantName, email, mailType));
    }

    public boolean verifyMail(String verificationCode) {
        return curatorDb.getPendingMailVerification(verificationCode)
                .filter(pendingMailVerification -> pendingMailVerification.getVerificationDeadline().isAfter(clock.instant()))
                .map(pendingMailVerification -> {
                    var tenant = requireCloudTenant(pendingMailVerification.getTenantName());
                    var oldTenantInfo = tenant.info();
                    var updatedTenantInfo = switch (pendingMailVerification.getMailType()) {
                        case NOTIFICATIONS -> withTenantContacts(oldTenantInfo, pendingMailVerification);
                        case TENANT_CONTACT -> oldTenantInfo.withContact(oldTenantInfo.contact()
                                .withEmail(oldTenantInfo.contact().email().withVerification(true)));
                        case BILLING -> withVerifiedBillingMail(oldTenantInfo);
                    };

                    tenantController.lockOrThrow(tenant.name(), LockedTenant.Cloud.class, lockedTenant -> {
                        lockedTenant = lockedTenant.withInfo(updatedTenantInfo);
                        tenantController.store(lockedTenant);
                    });

                    try (var lock = curatorDb.lockPendingMailVerification(pendingMailVerification.getVerificationCode())) {
                        curatorDb.deletePendingMailVerification(pendingMailVerification);
                    }
                    return true;
                }).orElse(false);
    }

    private TenantInfo withTenantContacts(TenantInfo oldInfo, PendingMailVerification pendingMailVerification) {
        var newContacts = oldInfo.contacts().ofType(TenantContacts.EmailContact.class)
                .stream()
                .map(contact -> {
                    if (pendingMailVerification.getMailAddress().equals(contact.email().getEmailAddress()))
                        return contact.withEmail(contact.email().withVerification(true));
                    return contact;
                }).toList();
        return oldInfo.withContacts(new TenantContacts(newContacts));
    }

    private TenantInfo withVerifiedBillingMail(TenantInfo oldInfo) {
        var verifiedMail = oldInfo.billingContact().contact().email().withVerification(true);
        var billingContact = oldInfo.billingContact()
                .withContact(oldInfo.billingContact().contact().withEmail(verifiedMail));
        return oldInfo.withBilling(billingContact);
    }

    private void writePendingVerification(PendingMailVerification pendingMailVerification) {
        try (var lock = curatorDb.lockPendingMailVerification(pendingMailVerification.getVerificationCode())) {
            curatorDb.writePendingMailVerification(pendingMailVerification);
        }
    }

    private CloudTenant requireCloudTenant(TenantName tenantName) {
        return tenantController.get(tenantName)
                .filter(tenant -> tenant.type() == Tenant.Type.cloud)
                .map(CloudTenant.class::cast)
                .orElseThrow(() -> new IllegalStateException("Mail verification is only applicable for cloud tenants"));
    }

    private Mail mailOf(PendingMailVerification pendingMailVerification) {
        var classLoader = this.getClass().getClassLoader();
        var template = uncheck(() -> classLoader.getResourceAsStream("mail/mail-verification.tmpl").readAllBytes());
        var message = new String(template)
                .replaceAll("%\\{consoleUrl}", dashboardUri.getHost())
                .replaceAll("%\\{email}", pendingMailVerification.getMailAddress())
                .replaceAll("%\\{code}", pendingMailVerification.getVerificationCode());
        return new Mail(List.of(pendingMailVerification.getMailAddress()), "Please verify your email", "", message);
    }

}
