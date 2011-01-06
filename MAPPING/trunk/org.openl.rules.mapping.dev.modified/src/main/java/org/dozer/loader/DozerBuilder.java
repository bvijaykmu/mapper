/*
 * Copyright 2005-2009 the original author or authors.
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
package org.dozer.loader;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.dozer.CustomConverter;
import org.dozer.classmap.ClassMap;
import org.dozer.classmap.Configuration;
import org.dozer.classmap.CopyByReference;
import org.dozer.classmap.DozerClass;
import org.dozer.classmap.MappingDirection;
import org.dozer.classmap.MappingFileData;
import org.dozer.classmap.RelationshipType;
import org.dozer.converters.CustomConverterDescription;
import org.dozer.converters.InstanceCustomConverterDescription;
import org.dozer.converters.JavaClassCustomConverterDescription;
import org.dozer.fieldmap.CustomGetSetMethodFieldMap;
import org.dozer.fieldmap.DozerField;
import org.dozer.fieldmap.ExcludeFieldMap;
import org.dozer.fieldmap.FieldMap;
import org.dozer.fieldmap.FieldMapUtils;
import org.dozer.fieldmap.GenericFieldMap;
import org.dozer.fieldmap.HintContainer;
import org.dozer.fieldmap.MapFieldMap;
import org.dozer.fieldmap.MultiSourceFieldMap;
import org.dozer.util.DozerConstants;
import org.dozer.util.MappingUtils;

/**
 * Builder API for achivieng the same effect as custom Xml mappings. Is intended
 * to be used from application to prepare repetetive mappings programmatically.
 * <p/>
 * Note that some of the fail-fast checks from Xml validation has not yet been
 * ported. Responsibility on filling all mandatory attributes is left to API
 * user.
 * <p/>
 * Not thread safe
 * 
 * @author dmitry.buzdin
 */
public class DozerBuilder {

    MappingFileData data = new MappingFileData();
    private final List<MappingBuilder> mappingBuilders = new ArrayList<MappingBuilder>();

    public MappingFileData build() {
        for (MappingBuilder builder : mappingBuilders) {
            builder.build();
        }
        return data;
    }

    public ConfigurationBuilder configuration() {
        Configuration configuration = new Configuration();
        data.setConfiguration(configuration);
        return new ConfigurationBuilder(configuration);
    }

    public MappingBuilder mapping() {
        Configuration configuration = data.getConfiguration();
        ClassMap classMap = new ClassMap(configuration);
        data.getClassMaps().add(classMap);
        MappingBuilder mappingDefinitionBuilder = new MappingBuilder(classMap);
        mappingBuilders.add(mappingDefinitionBuilder);
        return mappingDefinitionBuilder;
    }

    public static class MappingBuilder {

        private ClassMap classMap;
        private final List<FieldBuider> fieldBuilders = new ArrayList<FieldBuider>();

        public MappingBuilder(ClassMap classMap) {
            this.classMap = classMap;
        }

        public MappingBuilder dateFormat(String dateFormat) {
            classMap.setDateFormat(dateFormat);
            return this;
        }

        public MappingBuilder mapNull(boolean value) {
            classMap.setMapNull(value);
            return this;
        }

        public MappingBuilder mapEmptyString(boolean value) {
            classMap.setMapEmptyString(value);
            return this;
        }

        // TODO Load class ?
        public MappingBuilder beanFactory(String typeName) {
            classMap.setBeanFactory(typeName);
            return this;
        }

        public MappingBuilder relationshipType(RelationshipType type) {
            classMap.setRelationshipType(type);
            return this;
        }

        public MappingBuilder wildcard(Boolean value) {
            classMap.setWildcard(value);
            return this;
        }

        public MappingBuilder trimStrings(Boolean value) {
            classMap.setTrimStrings(value);
            return this;
        }

        public MappingBuilder stopOnErrors(Boolean value) {
            classMap.setStopOnErrors(value);
            return this;
        }

        public MappingBuilder mapId(String id) {
            classMap.setMapId(id);
            return this;
        }

        public MappingBuilder type(MappingDirection type) {
            classMap.setType(type);
            return this;
        }

        public ClassDefinitionBuilder classA(String typeName) {
            Class<?> type = MappingUtils.loadClass(typeName);
            return classA(type);
        }

        public ClassDefinitionBuilder classA(Class<?> type) {
            DozerClass classDefinition = new DozerClass();
            classDefinition.setName(type.getName());
            classMap.setSrcClass(classDefinition);
            return new ClassDefinitionBuilder(classDefinition);
        }

        public ClassDefinitionBuilder classB(String typeName) {
            Class<?> type = MappingUtils.loadClass(typeName);
            return classB(type);
        }

        public ClassDefinitionBuilder classB(Class<?> type) {
            DozerClass classDefinition = new DozerClass();
            classDefinition.setName(type.getName());
            classMap.setDestClass(classDefinition);
            return new ClassDefinitionBuilder(classDefinition);
        }

        public FieldExclusionBuilder fieldExclude() {
            ExcludeFieldMap excludeFieldMap = new ExcludeFieldMap(classMap);
            FieldExclusionBuilder builder = new FieldExclusionBuilder(excludeFieldMap);
            fieldBuilders.add(builder);
            return builder;
        }

        public FieldMappingBuilder field() {
            FieldMappingBuilder builder = new FieldMappingBuilder(classMap);
            fieldBuilders.add(builder);
            return builder;
        }

        public void build() {
            for (FieldBuider builder : fieldBuilders) {
                builder.build();
            }
        }

    }

    public interface FieldBuider {
        FieldDefinitionBuilder a(String name, String type);

        FieldDefinitionBuilder b(String name, String type);

        void build();
    }

    public static class FieldExclusionBuilder implements FieldBuider {

        private ExcludeFieldMap fieldMap;

        public FieldExclusionBuilder(ExcludeFieldMap fieldMap) {
            this.fieldMap = fieldMap;
        }

        public void type(MappingDirection type) {
            fieldMap.setType(type);
        }

        public FieldDefinitionBuilder a(String name, String type) {
            DozerField field = prepareField(name, type);
            fieldMap.setSrcField(field);
            return new FieldDefinitionBuilder(field);
        }

        public FieldDefinitionBuilder b(String name, String type) {
            DozerField field = prepareField(name, type);
            fieldMap.setDestField(field);
            return new FieldDefinitionBuilder(field);
        }

        public void build() {
            ClassMap classMap = fieldMap.getClassMap();
            classMap.addFieldMapping(fieldMap);
        }
    }

    public static class FieldMappingBuilder implements FieldBuider {

        private ClassMap classMap;
        private List<DozerField> src = new ArrayList<DozerField>();
        private DozerField destField;
        private MappingDirection type;
        private RelationshipType relationshipType;
        private boolean removeOrphans;
        private boolean copyByReference;
        private String mapId;
        private String customConverter;
        private String customConverterId;
        private String customConverterParam;
        private boolean copyByReferenceSet;

        private String mappingCondition;
        private String mappingConditionId;

        public FieldMappingBuilder(ClassMap classMap) {
            this.classMap = classMap;
        }

        public FieldDefinitionBuilder a(String name) {
            return a(name, null);
        }

        public FieldDefinitionBuilder a(String name, String type) {
            DozerField field = DozerBuilder.prepareField(name, type);
            this.src.add(field);
            return new FieldDefinitionBuilder(field);
        }

        public FieldDefinitionBuilder b(String name) {
            return b(name, null);
        }

        public FieldDefinitionBuilder b(String name, String type) {
            DozerField field = prepareField(name, type);
            this.destField = field;
            return new FieldDefinitionBuilder(field);
        }

        public FieldMappingBuilder type(MappingDirection type) {
            this.type = type;
            return this;
        }

        public FieldMappingBuilder relationshipType(RelationshipType relationshipType) {
            this.relationshipType = relationshipType;
            return this;
        }

        public FieldMappingBuilder removeOrphans(boolean value) {
            this.removeOrphans = value;
            return this;
        }

        public FieldMappingBuilder copyByReference(boolean value) {
            this.copyByReferenceSet = true;
            this.copyByReference = value;
            return this;
        }

        public FieldMappingBuilder mapId(String attribute) {
            this.mapId = attribute;
            return this;
        }

        public FieldMappingBuilder customConverter(Class<? extends CustomConverter> type) {
            return customConverter(type.getName());
        }

        public FieldMappingBuilder customConverter(String typeName) {
            this.customConverter = typeName;
            return this;
        }

        public FieldMappingBuilder customConverterId(String attribute) {
            this.customConverterId = attribute;
            return this;
        }

        public FieldMappingBuilder customConverterParam(String attribute) {
            this.customConverterParam = attribute;
            return this;
        }

        public FieldMappingBuilder mappingCondition(String typeName) {
            this.mappingCondition = typeName;
            return this;
        }

        public FieldMappingBuilder mappingConditionId(String attribute) {
            this.mappingConditionId = attribute;
            return this;
        }

        public void build() {
            // TODO Check Map to Map mapping
            FieldMap result;

            if (src.size() > 1) {
                result = new MultiSourceFieldMap(classMap);
                ((MultiSourceFieldMap) result).setSrc(src);
            } else {
                DozerField srcField = src.get(0);

                if (srcField.isMapTypeCustomGetterSetterField() || destField.isMapTypeCustomGetterSetterField() || classMap
                    .isSrcClassMapTypeCustomGetterSetter() || classMap.isDestClassMapTypeCustomGetterSetter()) {
                    result = new MapFieldMap(classMap);
                } else if (srcField.isCustomGetterSetterField() || destField.isCustomGetterSetterField()) {
                    result = new CustomGetSetMethodFieldMap(classMap);
                } else {
                    result = new GenericFieldMap(classMap);
                }

                result.setSrcField(srcField);
            }

            result.setDestField(destField);
            result.setType(type);
            result.setRelationshipType(relationshipType);
            result.setRemoveOrphans(removeOrphans);

            if (copyByReferenceSet) {
                result.setCopyByReference(copyByReference);
            }
            result.setMapId(mapId);

            result.setCustomConverter(customConverter);
            result.setCustomConverterId(customConverterId);
            result.setCustomConverterParam(customConverterParam);
            result.setMappingCondition(mappingCondition);
            result.setMappingConditionId(mappingConditionId);

            classMap.addFieldMapping(result);
        }
    }

    public static class FieldDefinitionBuilder {
        private DozerField field;

        public FieldDefinitionBuilder(DozerField field) {
            this.field = field;
        }

        public void dateFormat(String attribute) {
            field.setDateFormat(attribute);
        }

        public void theGetMethod(String attribute) {
            field.setTheGetMethod(attribute);
        }

        public void theSetMethod(String attribute) {
            field.setTheSetMethod(attribute);
        }

        public void mapGetMethod(String attribute) {
            field.setMapGetMethod(attribute);
        }

        public void mapSetMethod(String attribute) {
            field.setMapSetMethod(attribute);
        }

        public void key(String attribute) {
            field.setKey(attribute);
        }

        public void createMethod(String attribute) {
            field.setCreateMethod(attribute);
        }

        public void accessible(boolean b) {
            field.setAccessible(b);
        }

        public void required(boolean b) {
            field.setRequired(b);
        }

        public void defaultValue(Object attribute) {
            field.setDefaultValue(attribute);
        }

        public void hint(Class<?>... types) {
            HintContainer hintContainer = FieldMapUtils.hint(types);
            field.setHintContainer(hintContainer);
        }

        public void hint(String types) {
            HintContainer hintContainer = FieldMapUtils.hint(types);
            field.setHintContainer(hintContainer);
        }

        public void deepHint(Class<?>... types) {
            HintContainer hintContainer = FieldMapUtils.hint(types);
            field.setDeepIndexHintContainer(hintContainer);
        }

        public void deepHint(String types) {
            HintContainer hintContainer = FieldMapUtils.hint(types);
            field.setHintContainer(hintContainer);
        }

        public DozerField build() {
            return field;
        }
    }

    private static DozerField prepareField(String name, String type) {
        String fieldName;
        String fieldType = null;
        if (isIndexed(name)) {
            fieldName = getFieldNameOfIndexedField(name);
        } else {
            fieldName = name;
        }
        if (StringUtils.isNotEmpty(type)) {
            fieldType = type;
        }
        DozerField field = new DozerField(fieldName, fieldType);
        if (isIndexed(name)) {
            field.setIndexed(true);
            field.setIndex(getIndexOfIndexedField(name));
        }
        return field;
    }

    private static boolean isIndexed(String fieldName) {
        return (fieldName != null) && (fieldName.matches(".+\\[.+\\]$"));
    }

    static String getFieldNameOfIndexedField(String fieldName) {
        return fieldName == null ? null : fieldName.replaceAll("\\[.+\\]$", "");
    }

    private static String getIndexOfIndexedField(String fieldName) {
        return fieldName.replaceAll(".*\\[", "").replaceAll("\\]", "");
    }

    public static class ClassDefinitionBuilder {

        private DozerClass definition;

        public ClassDefinitionBuilder(DozerClass definition) {
            this.definition = definition;
        }

        public ClassDefinitionBuilder mapGetMethod(String name) {
            definition.setMapGetMethod(name);
            return this;
        }

        public ClassDefinitionBuilder mapSetMethod(String name) {
            definition.setMapSetMethod(name);
            return this;
        }

        public ClassDefinitionBuilder beanFactory(String beanFactory) {
            definition.setBeanFactory(beanFactory);
            return this;
        }

        public ClassDefinitionBuilder factoryBeanId(String id) {
            definition.setFactoryBeanId(id);
            return this;
        }

        public ClassDefinitionBuilder createMethod(String name) {
            definition.setCreateMethod(name);
            return this;
        }

        public ClassDefinitionBuilder mapNull(Boolean value) {
            definition.setMapNull(value);
            return this;
        }

        public ClassDefinitionBuilder mapEmptyString(Boolean value) {
            definition.setMapEmptyString(value);
            return this;
        }

    }

    public static class ConfigurationBuilder {

        private Configuration configuration;

        private CustomConverterDescription converterDescription;

        public ConfigurationBuilder(Configuration configuration) {
            this.configuration = configuration;
        }

        public ConfigurationBuilder stopOnErrors(Boolean value) {
            configuration.setStopOnErrors(value);
            return this;
        }

        public ConfigurationBuilder dateFormat(String format) {
            configuration.setDateFormat(format);
            return this;
        }

        public ConfigurationBuilder wildcard(Boolean value) {
            configuration.setWildcard(value);
            return this;
        }

        public ConfigurationBuilder trimStrings(Boolean value) {
            configuration.setTrimStrings(value);
            return this;
        }

        public ConfigurationBuilder relationshipType(RelationshipType value) {
            if (value == null) {
                configuration.setRelationshipType(DozerConstants.DEFAULT_RELATIONSHIP_TYPE_POLICY);
            } else {
                configuration.setRelationshipType(value);
            }
            return this;
        }

        public ConfigurationBuilder beanFactory(String name) {
            configuration.setBeanFactory(name);
            return this;
        }

        public CustomConverterBuilder customConverter(String type) {
            Class<? extends CustomConverter> aClass = (Class<? extends CustomConverter>) MappingUtils.loadClass(type);
            return customConverter(aClass);
        }

        // TODO Constraint with Generic
        public CustomConverterBuilder customConverter(Class<? extends CustomConverter> type) {
            converterDescription = new JavaClassCustomConverterDescription();
            ((JavaClassCustomConverterDescription) converterDescription).setType(type);
            return customConverter(converterDescription);
        }

        public CustomConverterBuilder customConverter(CustomConverter customConverter) {
            converterDescription = new InstanceCustomConverterDescription();
            ((InstanceCustomConverterDescription) converterDescription).setInstance(customConverter);
            return customConverter(converterDescription);
        }

        private CustomConverterBuilder customConverter(CustomConverterDescription converterDescription) {
            configuration.getCustomConverters().addConverter(converterDescription);
            return new CustomConverterBuilder(converterDescription);
        }

        public ConfigurationBuilder copyByReference(String typeMask) {
            CopyByReference copyByReference = new CopyByReference(typeMask);
            configuration.getCopyByReferences().add(copyByReference);
            return this;
        }

        public ConfigurationBuilder allowedException(String type) {
            Class<?> exceptionType = MappingUtils.loadClass(type);
            return allowedException(exceptionType);
        }

        // TODO Restrict with generic
        public ConfigurationBuilder allowedException(Class type) {
            if (!RuntimeException.class.isAssignableFrom(type)) {
                MappingUtils
                    .throwMappingException("allowed-exception Type must extend java.lang.RuntimeException: " + type
                        .getName());
            }
            configuration.getAllowedExceptions().getExceptions().add(type);
            return this;
        }

    }

    public static class CustomConverterBuilder {
        private CustomConverterDescription converterDescription;

        public CustomConverterBuilder(CustomConverterDescription converterDescription) {
            this.converterDescription = converterDescription;
        }

        public CustomConverterBuilder classA(String type) {
            Class<?> aClass = MappingUtils.loadClass(type);
            return classA(aClass);
        }

        public CustomConverterBuilder classA(Class<?> type) {
            converterDescription.setClassA(type);
            return this;
        }

        public CustomConverterBuilder classB(String type) {
            Class<?> aClass = MappingUtils.loadClass(type);
            return classB(aClass);
        }

        public CustomConverterBuilder classB(Class<?> type) {
            converterDescription.setClassB(type);
            return this;
        }

    }

}
