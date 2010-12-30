/*
 * Copyright 2005-2010 the original author or authors.
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

package org.dozer.loader.api;

import org.dozer.CustomConverter;
import org.dozer.classmap.MappingDirection;
import org.dozer.classmap.RelationshipType;
import org.dozer.loader.DozerBuilder;

/**
 * @author Dmitry Buzdin
 */
public class FieldsMappingOptions extends TypeMappingOptions {

    public static FieldsMappingOption copyByReference() {
        return new FieldsMappingOption() {
            public void apply(DozerBuilder.FieldMappingBuilder fieldMappingBuilder) {
                fieldMappingBuilder.copyByReference(true);
            }
        };
    }

    public static FieldsMappingOption customConverter(final Class<? extends CustomConverter> type) {
        return customConverter(type, null);
    }

    public static FieldsMappingOption customConverter(final Class<? extends CustomConverter> type,
        final String parameter) {
        return customConverter(type.getName(), parameter);
    }

    public static FieldsMappingOption customConverter(final String type) {
        return customConverter(type, null);
    }

    public static FieldsMappingOption customConverter(final String type, final String parameter) {
        return new FieldsMappingOption() {
            public void apply(DozerBuilder.FieldMappingBuilder fieldMappingBuilder) {
                fieldMappingBuilder.customConverter(type);
                fieldMappingBuilder.customConverterParam(parameter);
            }
        };
    }

    public static FieldsMappingOption customConverterId(final String id) {
        return new FieldsMappingOption() {
            public void apply(DozerBuilder.FieldMappingBuilder fieldMappingBuilder) {
                fieldMappingBuilder.customConverterId(id);
            }
        };
    }

    public static FieldsMappingOption condition(final String type) {
        return new FieldsMappingOption() {
            public void apply(DozerBuilder.FieldMappingBuilder fieldMappingBuilder) {
                fieldMappingBuilder.mappingCondition(type);
            }
        };
    }

    public static FieldsMappingOption conditionId(final String id) {
        return new FieldsMappingOption() {
            public void apply(DozerBuilder.FieldMappingBuilder fieldMappingBuilder) {
                fieldMappingBuilder.mappingConditionId(id);
            }
        };
    }

    public static FieldsMappingOption useMapId(final String mapId) {
        return new FieldsMappingOption() {
            public void apply(DozerBuilder.FieldMappingBuilder fieldMappingBuilder) {
                fieldMappingBuilder.mapId(mapId);
            }
        };
    }

    public static FieldsMappingOption fieldOneWay() {
        return new FieldsMappingOption() {
            public void apply(DozerBuilder.FieldMappingBuilder fieldMappingBuilder) {
                fieldMappingBuilder.type(MappingDirection.ONE_WAY);
            }
        };
    }

    public static FieldsMappingOption collectionStrategy(final boolean removeOrphans,
        final RelationshipType relationshipType) {
        return new FieldsMappingOption() {
            public void apply(DozerBuilder.FieldMappingBuilder fieldMappingBuilder) {
                fieldMappingBuilder.removeOrphans(removeOrphans);
                fieldMappingBuilder.relationshipType(relationshipType);
            }
        };
    }
    
}
