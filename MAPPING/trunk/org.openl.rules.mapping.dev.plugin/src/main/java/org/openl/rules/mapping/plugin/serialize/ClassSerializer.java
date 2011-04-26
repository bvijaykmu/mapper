package org.openl.rules.mapping.plugin.serialize;

import java.beans.PropertyDescriptor;
import java.util.ArrayList;
import java.util.List;

import org.dozer.util.CollectionUtils;
import org.dozer.util.ReflectionUtils;

public class ClassSerializer {

    public static BeanEntry serialize(Class<?> clazz) {

        if (clazz == null) {
            return null;
        }

        BeanEntry bean = new BeanEntry();
        bean.setName(clazz.getName());
        bean.setExtendedType(getSuperclass(clazz));

        PropertyDescriptor[] propDescriptors = ReflectionUtils.getPropertyDescriptors(clazz);
        List<FieldEntry> fields = new ArrayList<FieldEntry>(propDescriptors.length);

        for (PropertyDescriptor propDescriptor : propDescriptors) {
            if (!"class".equals(propDescriptor.getName())) {
                FieldEntry field = new FieldEntry();
                field.setName(propDescriptor.getName());
                Class<?> propertyType = propDescriptor.getPropertyType();
                field.setType(propertyType);

                boolean isCollection = CollectionUtils.isCollection(propertyType);
                boolean isArray = CollectionUtils.isArray(propertyType);
                if (isCollection || isArray) {
                    field.setCollectionType(isArray ? CollectionType.ARRAY : CollectionType.COLLECTION);
                    field.setCollectionItemType(ReflectionUtils.getComponentType(propertyType,
                        propDescriptor,
                        Object.class));
                }

                fields.add(field);
            }
        }

        bean.setFields(fields);

        return bean;
    }

    public static List<BeanEntry> serialize(List<Class<?>> classes) {

        List<BeanEntry> beans = new ArrayList<BeanEntry>(classes.size());

        for (Class<?> clazz : classes) {
            beans.add(serialize(clazz));
        }

        return beans;
    }
    
    private static Class<?> getSuperclass(Class<?> clazz) {
        Class<?> superclass = clazz.getSuperclass();
        if (Object.class == superclass) {
            return null;
        }
        
        return superclass;
    }
}
