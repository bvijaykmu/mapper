package org.openl.rules.mapping.loader.dozer;

import org.dozer.loader.api.FieldDefinition;

public class FieldDefinitionBuilder {
    
    private FieldDefinition fieldDefinition;
    
    public FieldDefinitionBuilder(String name) {
        fieldDefinition = new FieldDefinition(name);
    }
    
    public FieldDefinitionBuilder required(boolean value) {
        fieldDefinition.required(value);
        return this;
    }

    public FieldDefinitionBuilder defaultValue(Object value) {
        fieldDefinition.defaultValue(value);
        return this;
    }
    
    public FieldDefinitionBuilder createMethod(String method) {
        fieldDefinition.createMethod(method);
        return this;
    }
    
    public FieldDefinition build() {
        return fieldDefinition;
    }
}
