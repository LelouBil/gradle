/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.problems.internal.services;

import org.gradle.api.problems.Problems;
import org.gradle.api.problems.internal.DefaultProblems;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.problems.internal.OperationListener;

public class ProblemsGlobalServices {

//    ProblemTransformer createProblemTransfomer(BuildOperationAncestryTracker buildOperationAncestryTracker, OperationListener operationListener) {
//        return new TaskPathLocationTransformer(buildOperationAncestryTracker, operationListener);
//    }
//
    OperationListener createOperationListener(BuildOperationListenerManager buildOperationListenerManager) {
        OperationListener operationListener = new OperationListener();
        buildOperationListenerManager.addListener(operationListener);
        return operationListener;
    }

    Problems createProblemsService(
        BuildOperationProgressEventEmitter buildOperationProgressEventEmitter
//        List<ProblemTransformer> transformers
//        ProblemDiagnosticsFactory problemDiagnosticsFactory
    ) {
        return new DefaultProblems(buildOperationProgressEventEmitter);
    }

}
