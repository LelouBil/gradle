/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.tooling.internal.consumer;

import org.gradle.tooling.Failure;
import org.gradle.tooling.events.FailureResultWithProblems;
import org.gradle.tooling.events.FinishEvent;
import org.gradle.tooling.events.OperationResult;
import org.gradle.tooling.events.ProgressEvent;
import org.gradle.tooling.events.ProgressListener;
import org.gradle.tooling.events.problems.ProblemReport;

import java.util.List;

public class BuildFailedProgressListener implements ProgressListener {
    public List<? extends Failure> failures;
    public List<ProblemReport> problems;

    public BuildFailedProgressListener() {
    }

    @Override
    public void statusChanged(ProgressEvent event) {
        if (event instanceof FinishEvent && event.getDescriptor().getParent() == null) {
            System.out.println("! >  + event");
            System.out.println("! >  + event");
            OperationResult result = ((FinishEvent) event).getResult();
            if (result instanceof FailureResultWithProblems) {
                this.failures = ((FailureResultWithProblems) result).getFailures();
                this.problems = ((FailureResultWithProblems) result).getProblems();
            }
        }
    }
}
