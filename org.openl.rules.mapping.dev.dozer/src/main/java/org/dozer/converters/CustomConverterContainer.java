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
package org.dozer.converters;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.ClassUtils;
import org.apache.commons.lang.builder.ReflectionToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

/**
 * Internal class for holding custom converter definitions. Only intended for
 * internal use.
 * 
 * @author sullins.ben
 * @author dmitry.buzdin
 */
public class CustomConverterContainer {

    private List<CustomConverterDescription> converters = new ArrayList<CustomConverterDescription>();

    public List<CustomConverterDescription> getConverters() {
        return converters;
    }

    public void setConverters(List<CustomConverterDescription> converters) {
        if (converters == null) {
            throw new IllegalArgumentException("Converters can not be null!");
        }
        this.converters = converters;
    }

    public void addConverter(CustomConverterDescription converter) {
        getConverters().add(converter);
    }

    public CustomConverterDescription getCustomConverter(Class<?> srcClass, Class<?> destClass) {
        if (converters.isEmpty()) {
            return null;
        }

        // Let's see if the incoming class is a primitive:
        final Class<?> src = ClassUtils.primitiveToWrapper(srcClass);
        final Class<?> dest = ClassUtils.primitiveToWrapper(destClass);

        return findConverter(src, dest);
    }

    private CustomConverterDescription findConverter(Class<?> src, Class<?> dest) {
        // Otherwise, loop through custom converters and look for a match.
        //
        for (CustomConverterDescription customConverter : converters) {
            final Class<?> classA = ClassUtils.primitiveToWrapper(customConverter.getClassA());
            final Class<?> classB = ClassUtils.primitiveToWrapper(customConverter.getClassB());

            // we check to see if the destination class is the same as classA
            // defined in the converter mapping xml.
            // we next check if the source class is the same as classA defined
            // in the converter mapping xml.
            // we also to check to see if it is assignable to either. We then
            // perform these checks in the other direction for classB
            if ((classA.isAssignableFrom(dest) && classB.isAssignableFrom(src)) || (classA
                .isAssignableFrom(src) && classB.isAssignableFrom(dest))) {
                return customConverter;
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this, ToStringStyle.MULTI_LINE_STYLE);
    }

}