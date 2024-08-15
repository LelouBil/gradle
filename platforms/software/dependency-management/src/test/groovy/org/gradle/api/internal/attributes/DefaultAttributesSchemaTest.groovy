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

package org.gradle.api.internal.attributes

import org.gradle.api.Named
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchemaFactory
import org.gradle.api.internal.attributes.matching.AttributeSelectionSchema
import org.gradle.api.internal.attributes.matching.DefaultAttributeMatcher
import org.gradle.api.internal.attributes.matching.DefaultAttributeSelectionSchema
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

/**
 * Tests {@link DefaultAttributesSchema}.
 */
class DefaultAttributesSchemaTest extends Specification {
    def schema = AttributeTestUtil.mutableSchema()

    def selectionSchemaFor(AttributesSchemaInternal mutable) {
        ImmutableAttributesSchemaFactory factory = new ImmutableAttributesSchemaFactory()
        new DefaultAttributeSelectionSchema(factory.create(mutable))
    }

    def matcherFor(AttributeSelectionSchema schema) {
        new DefaultAttributeMatcher(schema)
    }

    def "can create an attribute of scalar type #type"() {
        when:
        Attribute.of('foo', type)

        then:
        noExceptionThrown()

        where:
        type << [
            String,
            Number,
            MyEnum,
            Flavor
        ]
    }

    def "can create an attribute of array type #type"() {
        when:
        Attribute.of('foo', type)

        then:
        noExceptionThrown()

        where:
        type << [
            String[].class,
            Number[].class,
            MyEnum[].class,
            Flavor[].class
        ]
    }

    enum MyEnum {
        FOO,
        BAR
    }

    def "fails if no strategy is declared for custom type"() {
        when:
        schema.getMatchingStrategy(Attribute.of('flavor', Flavor))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Unable to find matching strategy for flavor'
    }

    def "treats equal values as compatible when no rules defined"() {
        given:
        def attribute = Attribute.of(String)
        schema.attribute(attribute)

        def selectionSchema = selectionSchemaFor(schema)
        def matcher = matcherFor(selectionSchema)

        expect:
        !selectionSchema.matchValue(attribute, "a", "b")
        selectionSchema.matchValue(attribute, "a", "a")

        !matcher.isMatchingValue(attribute, "a", "b")
        matcher.isMatchingValue(attribute, "a", "a")
    }

    static class DoNothingRule implements AttributeCompatibilityRule<String> {
        static int count

        @Override
        void execute(CompatibilityCheckDetails<String> stringCompatibilityCheckDetails) {
            count++
        }
    }

    def "treats equal values as compatible when no rule expresses an opinion"() {
        given:
        def attribute = Attribute.of(String)
        schema.attribute(attribute).compatibilityRules.add(DoNothingRule)

        def selectionSchema = selectionSchemaFor(schema)
        def matcher = matcherFor(selectionSchema)

        expect:
        !selectionSchema.matchValue(attribute, "a", "b")
        selectionSchema.matchValue(attribute, "a", "a")

        !matcher.isMatchingValue(attribute, "a", "b")
        matcher.isMatchingValue(attribute, "a", "a")
    }

    static class BrokenRule implements AttributeCompatibilityRule<String> {
        @Override
        void execute(CompatibilityCheckDetails<String> stringCompatibilityCheckDetails) {
            throw new RuntimeException()
        }
    }

    def "short-circuits evaluation when values are equal"() {
        given:
        def attribute = Attribute.of(String)
        schema.attribute(attribute).compatibilityRules.add(BrokenRule)

        def selectionSchema = selectionSchemaFor(schema)
        def matcher = matcherFor(selectionSchema)

        expect:
        selectionSchema.matchValue(attribute, "a", "a")

        matcher.isMatchingValue(attribute, "a", "a")
    }

    def "strategy is per attribute"() {
        given:
        schema.attribute(Attribute.of('a', Flavor))

        when:
        schema.getMatchingStrategy(Attribute.of('someOther', Flavor))

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Unable to find matching strategy for someOther'

        when:
        schema.getMatchingStrategy(Attribute.of('picard', Flavor))

        then:
        e = thrown(IllegalArgumentException)
        e.message == 'Unable to find matching strategy for picard'
    }

    static class CustomCompatibilityRule implements AttributeCompatibilityRule<Flavor> {
        @Override
        void execute(CompatibilityCheckDetails<Flavor> details) {
            def producerValue = details.producerValue
            def consumerValue = details.consumerValue
            if (producerValue.name.length() > consumerValue.name.length()) {
                // arbitrary, just for testing purposes
                details.compatible()
            }
        }
    }

    def "compatibility rules can mark values as compatible"() {
        def attr = Attribute.of(Flavor)

        given:
        schema.attribute(attr).compatibilityRules.add(CustomCompatibilityRule)

        def value1 = flavor('value')
        def value2 = flavor('otherValue')

        def selectionSchema = selectionSchemaFor(schema)
        def matcher = matcherFor(selectionSchema)

        expect:
        selectionSchema.matchValue(attr, value1, value2)
        !selectionSchema.matchValue(attr, value2, value1)

        matcher.isMatchingValue(attr, value2, value1)
        !matcher.isMatchingValue(attr, value1, value2)
    }

    static class IncompatibleStringsRule implements AttributeCompatibilityRule<String> {
        @Override
        void execute(CompatibilityCheckDetails<String> details) {
            details.incompatible()
        }
    }

    def "compatibility rules can mark values as incompatible and short-circuit evaluation"() {
        def attr = Attribute.of(String)

        given:
        schema.attribute(attr).compatibilityRules.add(IncompatibleStringsRule)
        schema.attribute(attr).compatibilityRules.add(BrokenRule)

        def selectionSchema = selectionSchemaFor(schema)
        def matcher = matcherFor(selectionSchema)

        expect:
        !selectionSchema.matchValue(attr, "a", "b")

        !matcher.isMatchingValue(attr, "a", "b")
    }

    def "precedence order can be set"() {
        when:
        schema.attributeDisambiguationPrecedence(Attribute.of("a", Flavor), Attribute.of("b", String), Attribute.of("c", ConcreteNamed))
        then:
        schema.attributeDisambiguationPrecedence*.name == [ "a", "b", "c" ]
        when:
        schema.attributeDisambiguationPrecedence = [Attribute.of("c", ConcreteNamed)]
        then:
        schema.attributeDisambiguationPrecedence*.name == [ "c" ]
        when:
        schema.attributeDisambiguationPrecedence(Attribute.of("a", Flavor))
        then:
        schema.attributeDisambiguationPrecedence*.name == [ "c", "a" ]
    }

    def "precedence order cannot be changed for the same attribute"() {
        when:
        schema.attributeDisambiguationPrecedence(Attribute.of("a", Flavor), Attribute.of("b", String), Attribute.of("c", ConcreteNamed))
        schema.attributeDisambiguationPrecedence(Attribute.of("a", Flavor))
        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Attribute 'a' precedence has already been set."
    }

    static interface Flavor extends Named {}

    static Flavor flavor(String name) {
        TestUtil.objectInstantiator().named(Flavor, name)
    }

    static abstract class ConcreteNamed implements Named {
    }

}
