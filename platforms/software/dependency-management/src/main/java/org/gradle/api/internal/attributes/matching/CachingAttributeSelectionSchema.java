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

package org.gradle.api.internal.attributes.matching;

import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.attributes.ImmutableAttributes;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches results of a delegate {@link AttributeSelectionSchema}. Not all methods
 * are cached, as we only want to add caching to methods that have been proven
 * to be expensive.
 */
public class CachingAttributeSelectionSchema implements AttributeSelectionSchema {

    private final AttributeSelectionSchema delegate;

    private final Map<ExtraAttributesEntry, Attribute<?>[]> extraAttributesCache = new ConcurrentHashMap<>();

    public CachingAttributeSelectionSchema(AttributeSelectionSchema delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean hasAttribute(Attribute<?> attribute) {
        return delegate.hasAttribute(attribute);
    }

    @Override
    public <T> Set<T> disambiguate(Attribute<T> attribute, @Nullable T requested, Set<T> candidates) {
        return delegate.disambiguate(attribute, requested, candidates);
    }

    @Override
    public <T> boolean matchValue(Attribute<T> attribute, T requested, T candidate) {
        return delegate.matchValue(attribute, requested, candidate);
    }

    @Override
    public Attribute<?> getAttribute(String name) {
        return delegate.getAttribute(name);
    }

    @Override
    public Attribute<?>[] collectExtraAttributes(ImmutableAttributes[] candidateAttributeSets, ImmutableAttributes requested) {
        // It's almost always the same attribute sets which are compared, so in order to avoid a lot of memory allocation
        // during computation of the intersection, we cache the result here.
        ExtraAttributesEntry entry = new ExtraAttributesEntry(candidateAttributeSets, requested);
        return extraAttributesCache.computeIfAbsent(entry, key -> delegate.collectExtraAttributes(key.candidateAttributeSets, key.requestedAttributes));
    }

    /**
     * A cache entry key, leveraging _identity_ as the key, because we do interning.
     * This is a performance optimization.
     */
    private static class ExtraAttributesEntry {
        private final ImmutableAttributes[] candidateAttributeSets;
        private final ImmutableAttributes requestedAttributes;
        private final int hashCode;

        private ExtraAttributesEntry(ImmutableAttributes[] candidateAttributeSets, ImmutableAttributes requestedAttributes) {
            this.candidateAttributeSets = candidateAttributeSets;
            this.requestedAttributes = requestedAttributes;
            int hash = Arrays.hashCode(candidateAttributeSets);
            hash = 31 * hash + requestedAttributes.hashCode();
            this.hashCode = hash;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ExtraAttributesEntry that = (ExtraAttributesEntry) o;
            if (requestedAttributes != that.requestedAttributes) {
                return false;
            }
            if (candidateAttributeSets.length != that.candidateAttributeSets.length) {
                return false;
            }
            for (int i = 0; i < candidateAttributeSets.length; i++) {
                if (candidateAttributeSets[i] != that.candidateAttributeSets[i]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    @Override
    public PrecedenceResult orderByPrecedence(Collection<Attribute<?>> requested) {
        return delegate.orderByPrecedence(requested);
    }
}
