/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.internal.artifacts.VariantTransformRegistry;
import org.gradle.api.internal.attributes.AttributeSchemaServiceFactory;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler;

import javax.inject.Inject;

public class DefaultVariantSelectorFactory implements VariantSelectorFactory {
    private final ImmutableAttributesFactory attributesFactory;
    private final AttributeSchemaServiceFactory attributeSchemaServices;
    private final IsolatingTransformFinder transformFinder;
    private final TransformedVariantFactory transformedVariantFactory;
    private final ResolutionFailureHandler failureProcessor;
    private final VariantTransformRegistry transformRegistry;

    @Inject
    public DefaultVariantSelectorFactory(
        ImmutableAttributesFactory attributesFactory,
        AttributeSchemaServiceFactory attributeSchemaServices,
        IsolatingTransformFinder transformFinder,
        TransformedVariantFactory transformedVariantFactory,
        ResolutionFailureHandler failureProcessor,
        VariantTransformRegistry transformRegistry
    ) {
        this.attributesFactory = attributesFactory;
        this.attributeSchemaServices = attributeSchemaServices;
        this.transformFinder = transformFinder;
        this.transformedVariantFactory = transformedVariantFactory;
        this.failureProcessor = failureProcessor;
        this.transformRegistry = transformRegistry;
    }

    @Override
    public ArtifactVariantSelector create(ImmutableAttributesSchema schema, TransformUpstreamDependenciesResolverFactory dependenciesResolverFactory) {
        return new AttributeMatchingArtifactVariantSelector(transformRegistry, schema, attributesFactory, attributeSchemaServices, transformFinder, transformedVariantFactory, dependenciesResolverFactory, failureProcessor);
    }
}
