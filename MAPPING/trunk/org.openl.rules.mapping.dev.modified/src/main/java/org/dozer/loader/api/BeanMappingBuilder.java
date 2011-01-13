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

import org.dozer.classmap.MappingFileData;
import org.dozer.loader.DozerBuilder;
import org.dozer.util.DozerConstants;

/**
 * @author Dmitry Buzdin
 */
public abstract class BeanMappingBuilder extends MappingOptions {

    private DozerBuilder dozerBuilder;

    public BeanMappingBuilder() {
    }

    public MappingFileData build() {
        dozerBuilder = new DozerBuilder();
        configure();
        return dozerBuilder.build();
    }
    
    public BeanMappingBuilder config(ConfigurationMappingOption... configMappingOption) {
        DozerBuilder.ConfigurationBuilder configBuilder = dozerBuilder.configuration();
        
        for (ConfigurationMappingOption option : configMappingOption) {
            option.apply(configBuilder);
        }
        
        return this;
    }

    public TypeMappingBuilder mapping(String typeA, String typeB, TypeMappingOption... typeMappingOption) {
        return mapping(new TypeDefinition(typeA), new TypeDefinition(typeB), typeMappingOption);
    }

    public TypeMappingBuilder mapping(TypeDefinition typeA, String typeB, TypeMappingOption... typeMappingOption) {
        return mapping(typeA, new TypeDefinition(typeB), typeMappingOption);
    }

    public TypeMappingBuilder mapping(String typeA, TypeDefinition typeB, TypeMappingOption... typeMappingOption) {
        return mapping(new TypeDefinition(typeA), typeB, typeMappingOption);
    }

    public TypeMappingBuilder mapping(Class<?> typeA, Class<?> typeB, TypeMappingOption... typeMappingOption) {
        return mapping(new TypeDefinition(typeA), new TypeDefinition(typeB), typeMappingOption);
    }

    public TypeMappingBuilder mapping(TypeDefinition typeA, Class<?> typeB, TypeMappingOption... typeMappingOption) {
        return mapping(typeA, new TypeDefinition(typeB), typeMappingOption);
    }

    public TypeMappingBuilder mapping(Class<?> typeA, TypeDefinition typeB, TypeMappingOption... typeMappingOption) {
        return mapping(new TypeDefinition(typeA), typeB, typeMappingOption);
    }

    public TypeMappingBuilder mapping(TypeDefinition typeA, TypeDefinition typeB,
        TypeMappingOption... typeMappingOption) {
        DozerBuilder.MappingBuilder mappingBuilder = dozerBuilder.mapping();
        DozerBuilder.ClassDefinitionBuilder typeBuilderA = mappingBuilder.classA(typeA.getName());
        DozerBuilder.ClassDefinitionBuilder typeBuilderB = mappingBuilder.classB(typeB.getName());

        typeA.build(typeBuilderA);
        typeB.build(typeBuilderB);

        for (TypeMappingOption option : typeMappingOption) {
            option.apply(mappingBuilder);
        }

        return new TypeMappingBuilder(mappingBuilder);
    }

    public TypeDefinition type(String name) {
        return new TypeDefinition(name);
    }

    public TypeDefinition type(Class<?> type) {
        return new TypeDefinition(type);
    }

    public FieldDefinition field(String name) {
        return new FieldDefinition(name);
    }

    public FieldDefinition[] multi(String... names) {
        if (names == null) {
            return new FieldDefinition[0];
        }

        FieldDefinition[] definitions = new FieldDefinition[names.length];
        
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            definitions[i] = new FieldDefinition(name);
        }

        return definitions;
    }
    
    public FieldDefinition this_() {
        return new FieldDefinition(DozerConstants.SELF_KEYWORD);
    }

    protected abstract void configure();

}
