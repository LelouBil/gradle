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

package org.gradle.api.internal.attributes.matching

import com.google.common.collect.ImmutableMap
import org.gradle.api.Named
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.attributes.Usage
import org.gradle.api.internal.attributes.AttributesSchemaInternal
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchemaFactory
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

import static org.gradle.util.TestUtil.objectFactory

/**
 * Tests {@link DefaultAttributeSelectionSchema}.
 */
class DefaultAttributeSelectionSchemaTest extends Specification {

    def "collects extra attributes, single candidate"() {
        def attr1 = Attribute.of("foo", String)
        def attr2 = Attribute.of("bar", String)

        given:
        ImmutableAttributes[] candidates = [candidate(ImmutableMap.of(attr1, "v1", attr2, "v2"))]
        AttributeContainer requested = attribute(attr1, "v3")

        when:
        def extraAttributes = newSelectionSchema().collectExtraAttributes(candidates, requested).toList()

        then:
        extraAttributes.contains(attr2)
        !extraAttributes.contains(attr1)
    }

    def "collects extra attributes, two candidates"() {
        def attr1 = Attribute.of("foo", String)
        def attr2 = Attribute.of("bar", String)
        def attr3 = Attribute.of("baz", String)

        given:
        ImmutableAttributes[] candidates = [
            candidate(ImmutableMap.of(attr1, "v1", attr2, "v2")),
            candidate(ImmutableMap.of(attr2, "v1", attr3, "v2"))
        ]
        AttributeContainer requested = attribute(attr1, "v3")

        when:
        def extraAttributes = newSelectionSchema().collectExtraAttributes(candidates, requested).toList()

        then:
        extraAttributes.contains(attr2)
        extraAttributes.contains(attr3)
        !extraAttributes.contains(attr1)
    }

    def "prefers extra attributes from the selection schema"() {
        def foo1 = Attribute.of("foo", String)
        def foo2 = Attribute.of("foo", String)
        def attr3 = Attribute.of("baz", String)

        given:
        ImmutableAttributes[] candidates = [candidate(ImmutableMap.of(attr3, "v2", foo1, "v2"))]
        AttributeContainer requested = attribute(attr3, "v3")
        def schema = newSelectionSchema {
            attribute(foo2)
        }

        when:
        def extraAttributes = schema.collectExtraAttributes(candidates, requested).toList()

        then:
        extraAttributes.contains(foo2)
        extraAttributes.every {
            !it.is(foo1)
        }
        !extraAttributes.contains(attr3)
    }

    def "ignores attribute type when computing extra attributes"() {
        def attr1 = Attribute.of("foo", String)
        def attr2 = Attribute.of("foo", Usage)

        given:

        ImmutableAttributes[] candidates = [candidate(ImmutableMap.of(attr2, objectFactory().named(Usage, "foo")))]
        AttributeContainer requested = attribute(attr1, "v3")

        expect:
        newSelectionSchema().collectExtraAttributes(candidates, requested).length == 0
    }

    def "selects requested value when it is one of the candidate values and no rules defined"() {
        def attr = Attribute.of(String)
        def schema = newSelectionSchema {
            attribute(attr)
        }

        expect:
        schema.disambiguate(attr, "bar", ["foo", "bar"] as Set) == ["bar"] as Set
    }

    def "returns null when no disambiguation rules and requested is not one of the candidate values"() {
        def attr = Attribute.of(String)
        def schema = newSelectionSchema {
            attribute(attr)
        }

        expect:
        schema.disambiguate(attr, "other", ["foo", "bar"] as Set) == null
    }

    static class DoNothingSelectionRule implements AttributeDisambiguationRule<String> {
        @Override
        void execute(MultipleCandidatesDetails<String> stringMultipleCandidatesDetails) {
        }
    }

    def "selects requested value when it is one of the candidate values and no rule expresses an opinion"() {
        def attr = Attribute.of(String)
        def schema = newSelectionSchema {
            attribute(attr).disambiguationRules.add(DoNothingSelectionRule)
        }

        expect:
        schema.disambiguate(attr, "bar", ["foo", "bar"] as Set) == ["bar"] as Set
    }

    def "returns null when no rule expresses an opinion and requested is not one of the candidate values"() {
        def attr = Attribute.of(String)
        def schema = newSelectionSchema {
            attribute(attr).disambiguationRules.add(DoNothingSelectionRule)
        }

        expect:
        schema.disambiguate(attr, "other", ["foo", "bar"] as Set) == null
    }

    static class CustomSelectionRule implements AttributeDisambiguationRule<Flavor> {
        @Override
        void execute(MultipleCandidatesDetails<Flavor> details) {
            details.closestMatch(details.candidateValues.first())
        }
    }

    def "custom rule can select best match"() {
        def attr = Attribute.of(Flavor)
        def schema = newSelectionSchema {
            attribute(attr).disambiguationRules.add(CustomSelectionRule)
        }

        def value1 = flavor('value1')
        def value2 = flavor('value2')
        def candidates = [value1, value2] as Set

        expect:
        schema.disambiguate(attr, flavor('requested'), candidates) == [value1] as Set
        schema.disambiguate(attr, value2, candidates) == [value1] as Set
    }

    def "precedence order is honored"() {
        def x = Attribute.of("x", String)
        def a = Attribute.of("a", Flavor)
        def b = Attribute.of("b", String)
        def c = Attribute.of("c", ConcreteNamed)

        def schema = newSelectionSchema {
            attributeDisambiguationPrecedence(a, b, c)
        }

        def requested = AttributeTestUtil.attributesTyped(
            // attribute that doesn't have a precedence
            (x): "x",
            // attribute that has a lower precedence than the next one
            (c): AttributeTestUtil.named(ConcreteNamed, "c"),
            // attribute with the highest precedence
            (a): flavor("a"),
            // attribute that doesn't have a precedence
            (Attribute.of("z", String)): "z"
        )

        when:
        def result = schema.orderByPrecedence(requested.keySet())

        then:
        result.sortedOrder == [2, 1]
        result.unsortedOrder as List == [0, 3]
    }

    def "requested attributes are not sorted when there is no attribute precedence"() {
        def x = Attribute.of("x", String)
        def a = Attribute.of("a", Flavor)
        def c = Attribute.of("c", ConcreteNamed)

        def schema = newSelectionSchema()

        def requested = AttributeTestUtil.attributesTyped(
            (x): "x",
            (c): AttributeTestUtil.named(ConcreteNamed, "c"),
            (a): flavor("a"),
            (Attribute.of("z", String)): "z"
        )

        when:
        def result = schema.orderByPrecedence(requested.keySet())

        then:
        result.sortedOrder == []
        result.unsortedOrder as List == [0, 1, 2, 3]
    }

    def "requested attributes are not sorted when there is a different set of attributes used for precedence"() {
        def x = Attribute.of("x", String)
        def a = Attribute.of("a", Flavor)
        def c = Attribute.of("c", ConcreteNamed)

        def schema = newSelectionSchema {
            attributeDisambiguationPrecedence(
                Attribute.of("notA", Flavor),
                Attribute.of("notB", String),
                Attribute.of("notC", ConcreteNamed)
            )
        }

        def requested = AttributeTestUtil.attributesTyped(
            (x): "x",
            (c): AttributeTestUtil.named(ConcreteNamed, "c"),
            (a): flavor("a"),
            (Attribute.of("z", String)): "z"
        )

        when:
        def result = schema.orderByPrecedence(requested.keySet())

        then:
        result.sortedOrder == []
        result.unsortedOrder as List == [0, 1, 2, 3]
    }

    private static attribute(Attribute<String> attr, String value) {
        def mutable = AttributeTestUtil.attributesFactory().mutable()
        mutable.attribute(attr, value)
        mutable.asImmutable()
    }

    private static <T> ImmutableAttributes candidate(Map<Attribute<T>, T> map) {
        def mutable = AttributeTestUtil.attributesFactory().mutable()
        map.each { mutable.attribute(it.key, it.value)}
        mutable.asImmutable()
    }

    private static AttributeSelectionSchema newSelectionSchema(@DelegatesTo(AttributesSchema) Closure<?> action = {}) {
        AttributesSchemaInternal attributesSchema = AttributeTestUtil.mutableSchema(action)
        ImmutableAttributesSchema immutable = new ImmutableAttributesSchemaFactory().create(attributesSchema)
        new DefaultAttributeSelectionSchema(immutable)
    }

    static interface Flavor extends Named {}

    static Flavor flavor(String name) {
        TestUtil.objectInstantiator().named(Flavor, name)
    }

    static abstract class ConcreteNamed implements Named {
    }
}
