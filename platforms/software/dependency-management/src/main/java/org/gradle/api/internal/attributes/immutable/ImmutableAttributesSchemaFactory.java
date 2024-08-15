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

package org.gradle.api.internal.attributes.immutable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.common.collect.Sets;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.DefaultAttributeMatchingStrategy;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.inject.Inject;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating and interning immutable attribute schemas.
 */
@ServiceScope(Scope.BuildSession.class)
public class ImmutableAttributesSchemaFactory {

    private final Interner<ImmutableAttributesSchema> schemas = Interners.newStrongInterner();
    private final Map<SchemaPair, ImmutableAttributesSchema> mergedSchemas = new ConcurrentHashMap<>();

    @Inject
    @SuppressWarnings("CheckReturnValue")
    public ImmutableAttributesSchemaFactory() {
        schemas.intern(ImmutableAttributesSchema.EMPTY);
    }

    /**
     * Create an immutable schema from its raw components, interning the result.
     *
     * @param strategies The attribute matching strategies.
     * @param precedence The attribute matching precedence. Order is significant. Must not contain duplicates.
     *
     * @return The new immutable schema.
     */
    public ImmutableAttributesSchema create(
        ImmutableMap<Attribute<?>, ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<?>> strategies,
        ImmutableList<Attribute<?>> precedence
    ) {
        return schemas.intern(new ImmutableAttributesSchema(
            strategies,
            precedence
        ));
    }

    /**
     * Create a new immutable schema from the given mutable schema, interning the result.
     *
     * @param mutable The mutable schema to convert.
     *
     * @return The new immutable schema.
     */
    public ImmutableAttributesSchema create(AttributesSchemaInternal mutable) {
        // TODO: "Lock in" the mutable schema once we create an immutable copy of it,
        // as to prevent further mutations that will be ignored.
        return create(
            convertStrategies(mutable),
            ImmutableList.copyOf(mutable.getAttributePrecedence())
        );
    }

    private static ImmutableMap<Attribute<?>, ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<?>> convertStrategies(AttributesSchemaInternal mutable) {
        ImmutableMap.Builder<Attribute<?>, ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<?>> strategies = ImmutableMap.builder();
        for (Map.Entry<Attribute<?>, DefaultAttributeMatchingStrategy<?>> entry : mutable.getStrategies().entrySet()) {
            strategies.put(entry.getKey(), convertStrategy(entry.getValue()));
        }
        return strategies.build();
    }

    private static <T> ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<T> convertStrategy(
        DefaultAttributeMatchingStrategy<T> mutableStrategy
    ) {
        return new ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<>(
            ImmutableList.copyOf(mutableStrategy.getCompatibilityRules().getRules()),
            ImmutableList.copyOf(mutableStrategy.getDisambiguationRules().getRules())
        );
    }

    /**
     * Merges two immutable schemas into a single schema, interning the result.
     *
     * @param consumer The schema from the consumer side.
     * @param producer The schema from the producer side.
     *
     * @return The merged schema.
     */
    public ImmutableAttributesSchema concat(ImmutableAttributesSchema consumer, ImmutableAttributesSchema producer) {
        return mergedSchemas.computeIfAbsent(new SchemaPair(consumer, producer), pair ->
            create(
                mergeStrategies(consumer, producer),
                mergePrecedence(consumer.precedence, producer.precedence)
            )
        );
    }

    private static class SchemaPair {
        private final ImmutableAttributesSchema consumer;
        private final ImmutableAttributesSchema producer;

        SchemaPair(ImmutableAttributesSchema consumer, ImmutableAttributesSchema producer) {
            this.consumer = consumer;
            this.producer = producer;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            SchemaPair other = (SchemaPair) obj;
            // We expect the consumer and producer to be interned
            return consumer == other.consumer && producer == other.producer;
        }

        @Override
        public int hashCode() {
            return Objects.hash(consumer, producer);
        }
    }

    /**
     * Merge the attributes matching strategies of a consumer and producer schema, with the entries from the
     * consumer taking precedence over the producer.
     */
    private static ImmutableMap<Attribute<?>, ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<?>> mergeStrategies(
        ImmutableAttributesSchema consumer,
        ImmutableAttributesSchema producer
    ) {
        ImmutableMap.Builder<Attribute<?>, ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<?>> builder = ImmutableMap.builder();
        for (Attribute<?> attribute : Sets.union(consumer.strategies.keySet(), producer.strategies.keySet())) {
            builder.put(attribute, mergeStrategyFor(attribute, consumer, producer));
        }
        return builder.build();
    }

    private static <T> ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<T> mergeStrategyFor(
        Attribute<T> attribute,
        ImmutableAttributesSchema consumer,
        ImmutableAttributesSchema producer
    ) {
        ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<T> consumerStrategy = consumer.getStrategy(attribute);
        ImmutableAttributesSchema.ImmutableAttributeMatchingStrategy<T> producerStrategy = producer.getStrategy(attribute);

        assert consumerStrategy != null || producerStrategy != null;

        if (consumerStrategy == null) {
            return producerStrategy;
        } else if (producerStrategy == null) {
            return consumerStrategy;
        } else {
            return consumerStrategy.mergeWith(producerStrategy);
        }
    }

    /**
     * Merge two ordered sets, with the elements from the consumer taking precedence over the producer.
     */
    private static <T> ImmutableList<T> mergePrecedence(ImmutableList<T> consumer, ImmutableList<T> producer) {
        return ImmutableSet.<T>builder()
            .addAll(consumer)
            .addAll(producer) // "Elements appear in the resulting set in the same order they were first added to the builder"
            .build()
            .asList();
    }

}
