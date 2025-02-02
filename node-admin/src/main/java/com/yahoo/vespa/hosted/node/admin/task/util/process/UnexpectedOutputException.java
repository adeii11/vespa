// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.process;

/**
 * @author hakonhall
 */
@SuppressWarnings("serial")
public class UnexpectedOutputException extends ChildProcessException {
    /**
     * @param problem Problem description, e.g. "Output is not of the form ^NAME=VALUE$"
     */
    public UnexpectedOutputException(String problem, String commandLine, String possiblyHugeOutput) {
        super("output was not of the expected format: " + problem, commandLine, possiblyHugeOutput);
    }

    /**
     * @param problem Problem description, e.g. "Output is not of the form ^NAME=VALUE$"
     */
    public UnexpectedOutputException(RuntimeException cause,
                                     String problem,
                                     String commandLine,
                                     String possiblyHugeOutput) {
        super(cause, "output was not of the expected format: " + problem, commandLine, possiblyHugeOutput);
    }
}
