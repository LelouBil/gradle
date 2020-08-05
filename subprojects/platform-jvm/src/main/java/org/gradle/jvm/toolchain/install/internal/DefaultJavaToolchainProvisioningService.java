/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain.install.internal;

import org.gradle.api.GradleException;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.Optional;

public class DefaultJavaToolchainProvisioningService implements JavaToolchainProvisioningService {

    @Contextual
    private static class MissingToolchainException extends GradleException {

        public MissingToolchainException(JavaToolchainSpec spec, @Nullable Throwable cause) {
            super("Unable to download toolchain matching these requirements: " + spec.getDisplayName(), cause);
        }

    }

    private final AdoptOpenJdkRemoteBinary openJdkBinary;
    private final JdkCacheDirectory cacheDirProvider;

    @Inject
    public DefaultJavaToolchainProvisioningService(AdoptOpenJdkRemoteBinary openJdkBinary, JdkCacheDirectory cacheDirProvider) {
        this.openJdkBinary = openJdkBinary;
        this.cacheDirProvider = cacheDirProvider;
    }

    public Optional<File> tryInstall(JavaToolchainSpec spec) {
        try {
            final Optional<File> jdkArchive = openJdkBinary.download(spec);
            return jdkArchive.map(cacheDirProvider::provisionFromArchive);
        } catch (Exception e) {
            throw new MissingToolchainException(spec, e);
        }
    }

}
