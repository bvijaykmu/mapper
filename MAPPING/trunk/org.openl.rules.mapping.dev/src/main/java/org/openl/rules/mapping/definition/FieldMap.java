package org.openl.rules.mapping.definition;

public class FieldMap {

    private String fieldA;
    private String fieldB;
    private ConverterDescriptor converter;

    public String getFieldA() {
        return fieldA;
    }

    public void setFieldA(String fieldA) {
        this.fieldA = fieldA;
    }

    public String getFieldB() {
        return fieldB;
    }

    public void setFieldB(String fieldB) {
        this.fieldB = fieldB;
    }

    public ConverterDescriptor getConverter() {
        return converter;
    }

    public void setConverter(ConverterDescriptor converter) {
        this.converter = converter;
    }

}
