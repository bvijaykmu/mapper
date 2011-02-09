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
package org.dozer;

import static org.dozer.util.DozerConstants.BASE_CLASS;
import static org.dozer.util.DozerConstants.ITERATE;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;
import org.dozer.cache.Cache;
import org.dozer.cache.CacheKeyFactory;
import org.dozer.cache.CacheManager;
import org.dozer.cache.DozerCacheType;
import org.dozer.classmap.ClassMap;
import org.dozer.classmap.ClassMapBuilder;
import org.dozer.classmap.ClassMappings;
import org.dozer.classmap.Configuration;
import org.dozer.classmap.CopyByReferenceContainer;
import org.dozer.classmap.RelationshipType;
import org.dozer.converters.DateFormatContainer;
import org.dozer.converters.PrimitiveOrWrapperConverter;
import org.dozer.event.DozerEvent;
import org.dozer.event.DozerEventManager;
import org.dozer.event.DozerEventType;
import org.dozer.event.EventManager;
import org.dozer.factory.BeanCreationDirective;
import org.dozer.factory.DestBeanCreator;
import org.dozer.fieldmap.CustomGetSetMethodFieldMap;
import org.dozer.fieldmap.ExcludeFieldMap;
import org.dozer.fieldmap.FieldMap;
import org.dozer.fieldmap.FieldMappingCondition;
import org.dozer.fieldmap.HintContainer;
import org.dozer.fieldmap.MapFieldMap;
import org.dozer.fieldmap.MultiSourceFieldMap;
import org.dozer.stats.StatisticType;
import org.dozer.stats.StatisticsManager;
import org.dozer.util.CollectionUtils;
import org.dozer.util.DozerConstants;
import org.dozer.util.IteratorUtils;
import org.dozer.util.LogMsgFactory;
import org.dozer.util.MappingUtils;
import org.dozer.util.MappingValidator;
import org.dozer.util.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Internal Mapping Engine. Not intended for direct use by Application code.
 * This class does most of the heavy lifting and is very recursive in nature.
 * <p/>
 * This class is not threadsafe and is instantiated for each new mapping
 * request.
 * 
 * @author garsombke.franz
 * @author sullins.ben
 * @author tierney.matt
 * @author dmitry.buzdin
 * @author johnsen.knut-erik
 */
public class MappingProcessor implements Mapper {

    private static final Logger log = LoggerFactory.getLogger(MappingProcessor.class);

    private final ClassMappings classMappings;
    private final Configuration globalConfiguration;
    private final List<CustomConverter> customConverterObjects;
    private final Map<String, CustomConverter> customConverterObjectsWithId;
    private final StatisticsManager statsMgr;
    private final EventManager eventMgr;
    private final CustomFieldMapper customFieldMapper;

    private final List<FieldMappingCondition> conditionObjects;
    private final Map<String, FieldMappingCondition> conditionObjectsWithId;

    private final MappedFieldsTracker mappedFields = new MappedFieldsTracker();

    private final Cache converterByDestTypeCache;
    private final Cache superTypeCache;
    private final PrimitiveOrWrapperConverter primitiveOrWrapperConverter = new PrimitiveOrWrapperConverter();

    protected MappingProcessor(ClassMappings classMappings, Configuration globalConfiguration, CacheManager cacheMgr,
        StatisticsManager statsMgr, List<CustomConverter> customConverterObjects, DozerEventManager eventManager,
        CustomFieldMapper customFieldMapper, Map<String, CustomConverter> customConverterObjectsWithId,
        List<FieldMappingCondition> conditionObjects, Map<String, FieldMappingCondition> conditionObjectsWithId) {

        this.classMappings = classMappings;
        this.globalConfiguration = globalConfiguration;
        this.statsMgr = statsMgr;
        this.customConverterObjects = customConverterObjects;
        this.eventMgr = eventManager;
        this.customFieldMapper = customFieldMapper;
        this.converterByDestTypeCache = cacheMgr.getCache(DozerCacheType.CONVERTER_BY_DEST_TYPE.name());
        this.superTypeCache = cacheMgr.getCache(DozerCacheType.SUPER_TYPE_CHECK.name());
        this.customConverterObjectsWithId = customConverterObjectsWithId;
        this.conditionObjects = conditionObjects;
        this.conditionObjectsWithId = conditionObjectsWithId;
    }

    /* Mapper Interface Implementation */

    public <T> T map(final Object srcObj, final Class<T> destClass) {
        return map(srcObj, destClass, null);
    }

    public <T> T map(final Object srcObj, final Class<T> destClass, final String mapId) {
        MappingValidator.validateMappingRequest(srcObj, destClass);
        return map(srcObj, destClass, null, mapId);
    }

    public void map(final Object srcObj, final Object destObj) {
        map(srcObj, destObj, null);
    }

    public void map(final Object srcObj, final Object destObj, final String mapId) {
        MappingValidator.validateMappingRequest(srcObj, destObj);
        map(srcObj, null, destObj, mapId);
    }

    /* End of Mapper Interface Implementation */

    /**
     * Single point of entry for atomic mapping operations
     * 
     * @param srcObj source object
     * @param destClass destination class
     * @param destObj destination object
     * @param mapId mapping identifier
     * @param <T> destination object type
     * @return new or updated destination object
     */
    private <T> T map(Object srcObj, final Class<T> destClass, final T destObj, final String mapId) {
        srcObj = MappingUtils.deProxy(srcObj);

        Class<T> destType;
        T result;
        if (destClass == null) {
            destType = (Class<T>) destObj.getClass();
            result = destObj;
        } else {
            destType = destClass;
            result = null;
        }

        ClassMap classMap = null;
        try {
            classMap = getClassMap(srcObj.getClass(), destType, mapId);

            eventMgr.fireEvent(new DozerEvent(DozerEventType.MAPPING_STARTED, classMap, null, srcObj, result, null));

            // TODO Check if any proxy issues are here
            // Check to see if custom converter has been specified for this
            // mapping
            // combination. If so, just use it.
            CustomConverter converter = MappingUtils.findCustomConverter(converterByDestTypeCache,
                customConverterObjects, classMap.getCustomConverters(), srcObj.getClass(), destType);

            if (converter != null) {
                return (T) mapUsingCustomConverterInstance(converter, srcObj.getClass(), srcObj, destType, result,
                    null, true);
            }

            if (result == null) {
                result = (T) DestBeanCreator.create(new BeanCreationDirective(srcObj, classMap.getSrcClassToMap(),
                    classMap.getDestClassToMap(), destType, classMap.getDestClassBeanFactory(), classMap
                        .getDestClassBeanFactoryId(), classMap.getDestClassCreateMethod()));
            }
            map(classMap, srcObj, result, false, null);
        } catch (Throwable e) {
            MappingUtils.throwMappingException(e);
        }
        eventMgr.fireEvent(new DozerEvent(DozerEventType.MAPPING_FINISHED, classMap, null, srcObj, result, null));

        return result;
    }

    private void map(ClassMap classMap, Object srcObj, Object destObj, boolean bypassSuperMappings, String mapId) {
        srcObj = MappingUtils.deProxy(srcObj);

        // 1596766 - Recursive object mapping issue. Prevent recursive mapping
        // infinite loop. Keep a record of mapped fields
        // by storing the id of the sourceObj and the destObj to be mapped. This
        // can
        // be referred to later to avoid recursive mapping loops
        mappedFields.put(srcObj, destObj);

        // If class map hasnt already been determined, find the appropriate one
        // for
        // the src/dest object combination
        if (classMap == null) {
            classMap = getClassMap(srcObj.getClass(), destObj.getClass(), mapId);
        }

        Class<?> srcClass = srcObj.getClass();
        Class<?> destClass = destObj.getClass();

        // Check to see if custom converter has been specified for this mapping
        // combination. If so, just use it.
        CustomConverter converter = MappingUtils.findCustomConverter(converterByDestTypeCache, customConverterObjects,
            classMap.getCustomConverters(), srcClass, destClass);
        if (converter != null) {
            mapUsingCustomConverterInstance(converter, srcClass, srcObj, destClass, destObj, null, true);
            return;
        }

        // Now check for super class mappings. Process super class mappings
        // first.
        List<String> mappedParentFields = null;
        if (!bypassSuperMappings) {
            mappedParentFields = mapParentFields(classMap, srcObj, destObj, mapId);
        }

        // Perform mappings for each field. Iterate through Fields Maps for this
        // class mapping
        for (FieldMap fieldMapping : classMap.getFieldMaps()) {
            // Bypass field if it has already been mapped as part of super class
            // mappings.
            String key = MappingUtils.getMappedParentFieldKey(destObj, fieldMapping);
            if (mappedParentFields != null && mappedParentFields.contains(key)) {
                continue;
            }
            mapField(fieldMapping, srcObj, destObj);
        }
    }

    private List<String> mapParentFields(ClassMap classMap, Object srcObj, Object destObj, String mapId) {
        Collection<ClassMap> superMappings = new ArrayList<ClassMap>();
        List<String> mappedParentFields = new ArrayList<String>();
        Collection<ClassMap> superClasses = checkForSuperTypeMapping(srcObj.getClass(), destObj.getClass());
        superMappings.addAll(superClasses);

        List<String> overridedFieldMappings = getFieldMapKeys(destObj, classMap.getFieldMaps()); 
        
        if (!superMappings.isEmpty()) {
            mappedParentFields = processSuperTypeMapping(superMappings, srcObj, destObj, mapId, overridedFieldMappings);
        }
        
        return mappedParentFields;
    }

    private List<String> getFieldMapKeys(Object destObj, List<FieldMap> fieldMaps) {
        List<String> keys = new ArrayList<String>();
        
        for (FieldMap fieldMap : fieldMaps) {
            String key = MappingUtils.getMappedParentFieldKey(destObj, fieldMap);
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
        
        return keys;
    }

    private void mapField(FieldMap fieldMapping, Object srcObj, Object destObj) {

        // The field has been explicitly excluded from mapping. So just return,
        // as
        // no further processing is needed for this field
        if (fieldMapping instanceof ExcludeFieldMap) {
            return;
        }

        Object srcFieldValue = null;
        try {
            // If a custom field mapper was specified, then invoke it. If not,
            // or the
            // custom field mapper returns false(indicating the
            // field was not actually mapped by the custom field mapper),
            // proceed as
            // normal(use Dozer to map the field)
            srcFieldValue = fieldMapping.getSrcFieldValue(srcObj);
            boolean fieldMapped = false;
            if (customFieldMapper != null) {
                fieldMapped = customFieldMapper.mapField(srcObj, destObj, srcFieldValue, fieldMapping.getClassMap(),
                    fieldMapping);
            }

            if (!fieldMapped) {
                if (!(fieldMapping instanceof MultiSourceFieldMap) && fieldMapping.getDestFieldType() != null && fieldMapping
                    .getDestFieldType().equals(ITERATE)) {
                    // special logic for iterate feature
                    mapFromIterateMethodFieldMap(srcObj, destObj, srcFieldValue, fieldMapping);
                } else {
                    // either deep field map or generic map. The is the most
                    // likely
                    // scenario
                    mapFromFieldMap(srcObj, destObj, srcFieldValue, fieldMapping);
                }
            }

            statsMgr.increment(StatisticType.FIELD_MAPPING_SUCCESS_COUNT);

        } catch (Throwable e) {
            log.error(LogMsgFactory.createFieldMappingErrorMsg(srcObj, fieldMapping, srcFieldValue, destObj), e);
            statsMgr.increment(StatisticType.FIELD_MAPPING_FAILURE_COUNT);

            // check error handling policy.
            if (fieldMapping.isStopOnErrors()) {
                MappingUtils.throwMappingException(e);
            } else {
                // check if any Exceptions should be allowed to be thrown
                if (!fieldMapping.getClassMap().getAllowedExceptions().isEmpty() && e.getCause() instanceof InvocationTargetException) {
                    Throwable thrownType = ((InvocationTargetException) e.getCause()).getTargetException();
                    Class<? extends Throwable> exceptionClass = thrownType.getClass();
                    if (fieldMapping.getClassMap().getAllowedExceptions().contains(exceptionClass)) {
                        throw (RuntimeException) thrownType;
                    }
                }
                statsMgr.increment(StatisticType.FIELD_MAPPING_FAILURE_IGNORED_COUNT);
            }
        }
    }

    private void mapFromFieldMap(Object srcObj, Object destObj, Object srcFieldValue, FieldMap fieldMapping) {
        Class<?> destFieldType;
        if (fieldMapping instanceof CustomGetSetMethodFieldMap) {
            try {
                destFieldType = fieldMapping.getDestFieldWriteMethod(destObj.getClass()).getParameterTypes()[0];
            } catch (Throwable e) {
                // try traditional way
                destFieldType = fieldMapping.getDestFieldType(destObj.getClass());
            }
        } else {
            destFieldType = fieldMapping.getDestFieldType(destObj.getClass());
        }

        // 1476780 - 12/2006 mht - Add support for field level custom converters
        // Use field level custom converter if one was specified. Otherwise, map
        // or
        // recurse the object as normal
        // 1770440 - fdg - Using multiple instances of CustomConverter
        Object destFieldValue;

        boolean mapField = true;
        if (!MappingUtils.isBlankOrNull(fieldMapping.getMappingConditionId())) {
            // check condition using condition id
            if (conditionObjectsWithId != null && conditionObjectsWithId.containsKey(fieldMapping
                .getMappingConditionId())) {
                Class<?> srcFieldClass = srcFieldValue != null ? srcFieldValue.getClass() : fieldMapping
                    .getSrcFieldType(srcObj.getClass());
                FieldMappingCondition conditionInstance = conditionObjectsWithId.get(fieldMapping
                    .getMappingConditionId());
                Object existingValue = getExistingValue(fieldMapping, destObj, destFieldType);
                mapField = evaluateConditionInstance(conditionInstance, srcFieldClass, srcFieldValue, destFieldType,
                    existingValue);
            } else {
                throw new MappingException("Mapping condition instance not found with id:" + fieldMapping
                    .getMappingConditionId());
            }
        } else if (!MappingUtils.isBlankOrNull(fieldMapping.getMappingCondition())) {
            Class<?> srcFieldClass = srcFieldValue != null ? srcFieldValue.getClass() : fieldMapping
                .getSrcFieldType(srcObj.getClass());
            Class<?> conditionClass = MappingUtils.loadClass(fieldMapping.getMappingCondition());
            Object existingValue = getExistingValue(fieldMapping, destObj, destFieldType);
            mapField = evaluateCondition(conditionClass, srcFieldClass, srcFieldValue, destFieldType, existingValue,
                fieldMapping);
        }

        // If mapping condition have returned false value skip current field
        // mapping
        if (!mapField) {
            return;
        }

        if (!MappingUtils.isBlankOrNull(fieldMapping.getCustomConverterId())) {
            if (customConverterObjectsWithId != null && customConverterObjectsWithId.containsKey(fieldMapping
                .getCustomConverterId())) {
                Class<?> srcFieldClass = srcFieldValue != null ? srcFieldValue.getClass() : fieldMapping
                    .getSrcFieldType(srcObj.getClass());
                destFieldValue = mapUsingCustomConverterInstance(customConverterObjectsWithId.get(fieldMapping
                    .getCustomConverterId()), srcFieldClass, srcFieldValue, destFieldType, destObj, fieldMapping, false);
            } else {
                throw new MappingException("CustomConverter instance not found with id:" + fieldMapping
                    .getCustomConverterId());
            }
        } else if (MappingUtils.isBlankOrNull(fieldMapping.getCustomConverter())) {
            if (fieldMapping instanceof MultiSourceFieldMap) {
                MappingUtils.throwMappingException("Custom converter should be provided");
            }
            destFieldValue = mapOrRecurseObject(srcObj, srcFieldValue, destFieldType, fieldMapping, destObj);
        } else {
            Class<?> srcFieldClass = srcFieldValue != null ? srcFieldValue.getClass() : fieldMapping
                .getSrcFieldType(srcObj.getClass());
            Class<?> converterClass = MappingUtils.loadClass(fieldMapping.getCustomConverter());

            // get dest value using user defined converter for current field map
            destFieldValue = mapUsingCustomConverter(converterClass, srcFieldClass, srcFieldValue, destFieldType,
                destObj, fieldMapping, false);
        }

        Object destDefaultValue = null;
        if (fieldMapping.getDestFieldDefaultValue() != null) {
            
            if (DozerConstants.SELF_KEYWORD.equals(fieldMapping.getDestFieldDefaultValue())) {
                // If default value of destination field is "this" keyword we
                // create new instance of destination field type
                // and use as a default value object.
                //
                Class<?> srcFieldClass = fieldMapping.getSrcFieldType(srcObj.getClass());
                destDefaultValue = DestBeanCreator.create(new BeanCreationDirective(srcFieldValue, srcFieldClass,
                    destFieldType, destFieldType, null, null, fieldMapping
                        .getDestFieldCreateMethod() != null ? fieldMapping.getDestFieldCreateMethod() : null));
            } else {
                // If default value is provided we use appropriate converter to
                // convert string value to appropriate object
                //
                destDefaultValue = primitiveOrWrapperConverter.convert(fieldMapping.getDestFieldDefaultValue(),
                    destFieldType, new DateFormatContainer(fieldMapping.getDateFormat()));
            }
        }

        writeDestinationValue(destObj, destFieldValue, fieldMapping, srcObj, destDefaultValue);

        if (log.isDebugEnabled()) {
            log.debug(LogMsgFactory.createFieldMappingSuccessMsg(srcObj.getClass(), destObj.getClass(), fieldMapping
                .getSrcFieldName(), fieldMapping.getDestFieldName(), srcFieldValue, destFieldValue, fieldMapping
                .getClassMap().getMapId()));
        }
    }

    private boolean evaluateConditionInstance(FieldMappingCondition conditionInstance, Class<?> srcFieldClass,
        Object srcFieldValue, Class<?> destFieldType, Object destFieldValue) {
        return conditionInstance.mapField(srcFieldValue, destFieldValue, srcFieldClass, destFieldType);
    }

    private boolean evaluateCondition(Class<?> conditionClass, Class<?> srcFieldClass, Object srcFieldValue,
        Class<?> destFieldType, Object destFieldValue, FieldMap fieldMapping) {

        FieldMappingCondition conditionInstance = null;

        if (conditionObjects != null) {
            for (FieldMappingCondition conditionObject : conditionObjects) {
                if (conditionObject.getClass().isAssignableFrom(conditionClass)) {
                    // we have a match
                    conditionInstance = conditionObject;
                }
            }
        }
        // if converter object instances were not injected, then create new
        // instance
        // of the converter for each conversion
        // TODO : Should we really create it each time?
        if (conditionInstance == null) {
            conditionInstance = (FieldMappingCondition) ReflectionUtils.newInstance(conditionClass);
        }

        return evaluateConditionInstance(conditionInstance, srcFieldClass, srcFieldValue, destFieldType, destFieldValue);
    }

    private Object mapOrRecurseObject(Object srcObj, Object srcFieldValue, Class<?> destFieldType, FieldMap fieldMap,
        Object destObj) {
        Class<?> srcFieldClass = srcFieldValue != null ? srcFieldValue.getClass() : fieldMap.getSrcFieldType(srcObj
            .getClass());
        CustomConverter converter = MappingUtils.determineCustomConverter(fieldMap, converterByDestTypeCache,
            customConverterObjects, fieldMap.getClassMap().getCustomConverters(), srcFieldClass, destFieldType);

        // 1-2007 mht: Invoke custom converter even if the src value is null.
        // #1563795
        if (converter != null) {
            return mapUsingCustomConverterInstance(converter, srcFieldClass, srcFieldValue, destFieldType, destObj,
                fieldMap, false);
        }

        if (srcFieldValue == null) {
            return null;
        }

        String srcFieldName = fieldMap.getSrcFieldName();
        String destFieldName = fieldMap.getDestFieldName();

        // 1596766 - Recursive object mapping issue. Prevent recursive mapping
        // infinite loop
        // In case of "this->this" mapping this rule should be omitted as
        // processing is done on objects, which has been
        // just marked as mapped.
        if (!(DozerConstants.SELF_KEYWORD.equals(srcFieldName) && DozerConstants.SELF_KEYWORD.equals(destFieldName))) {
            Object alreadyMappedValue = mappedFields.getMappedValue(srcFieldValue, destFieldType);
            if (alreadyMappedValue != null) {
                return alreadyMappedValue;
            }
        }

        if (fieldMap.isCopyByReference()) {
            // just get the src and return it, no transformation.
            return srcFieldValue;
        }

        boolean isSrcFieldClassSupportedMap = MappingUtils.isSupportedMap(srcFieldClass);
        boolean isDestFieldTypeSupportedMap = MappingUtils.isSupportedMap(destFieldType);
        if (isSrcFieldClassSupportedMap && isDestFieldTypeSupportedMap) {
            return mapMap(srcObj, (Map<?, ?>) srcFieldValue, fieldMap, destObj);
        }
        if (fieldMap instanceof MapFieldMap && destFieldType.equals(Object.class)) {
            // TODO: find better place for this logic. try to encapsulate in
            // FieldMap?
            destFieldType = fieldMap.getDestHintContainer() != null ? fieldMap.getDestHintContainer().getHint()
                                                                   : srcFieldClass;
        }

        if (MappingUtils.isPrimitiveOrWrapper(srcFieldClass) || MappingUtils.isPrimitiveOrWrapper(destFieldType)) {
            // Primitive or Wrapper conversion
            if (fieldMap.getDestHintContainer() != null) {
                destFieldType = fieldMap.getDestHintContainer().getHint();
            }
            DateFormatContainer dfContainer = new DateFormatContainer(fieldMap.getDateFormat());

            // #1841448 - if trim-strings=true, then use a trimmed src string
            // value when converting to dest value
            Object convertSrcFieldValue = srcFieldValue;
            if (fieldMap.isTrimStrings() && srcFieldValue.getClass().equals(String.class)) {
                convertSrcFieldValue = ((String) srcFieldValue).trim();
            }

            if (fieldMap instanceof MapFieldMap && !MappingUtils.isPrimitiveOrWrapper(destFieldType)) {
                // This handles a very special/rare use case(see
                // indexMapping.xml + unit
                // test
                // testStringToIndexedSet_UsingMapSetMethod). If the
                // destFieldType is a
                // custom object AND has a String param
                // constructor, we don't want to construct the custom object
                // with the
                // src value because the map backed property
                // logic at lower layers handles setting the value on the custom
                // object.
                // Without this special logic, the
                // destination map backed custom object would contain a value
                // that is
                // the custom object dest type instead of the
                // desired src value.
                return primitiveOrWrapperConverter.convert(convertSrcFieldValue, convertSrcFieldValue.getClass(),
                    dfContainer);
            } else {
                return primitiveOrWrapperConverter.convert(convertSrcFieldValue, destFieldType, dfContainer);
            }
        }
        if (MappingUtils.isSupportedCollection(srcFieldClass) && (MappingUtils.isSupportedCollection(destFieldType))) {
            return mapCollection(srcObj, srcFieldValue, fieldMap, destObj);
        }

        if (MappingUtils.isEnumType(srcFieldClass, destFieldType)) {
            return mapEnum((Enum) srcFieldValue, (Class<Enum>) destFieldType);
        }

        // Default: Map from one custom data object to another custom data
        // object
        return mapCustomObject(fieldMap, destObj, destFieldType, srcFieldValue);
    }

    private <T extends Enum<T>> T mapEnum(Enum<T> srcFieldValue, Class<T> destFieldType) {
        String name = srcFieldValue.name();
        return Enum.valueOf(destFieldType, name);
    }

    private Object mapCustomObject(FieldMap fieldMap, Object destObj, Class<?> destFieldType, Object srcFieldValue) {
        srcFieldValue = MappingUtils.deProxy(srcFieldValue);

        // Custom java bean. Need to make sure that the destination object is
        // not
        // already instantiated.
        Object result = getExistingValue(fieldMap, destObj, destFieldType);
        ClassMap classMap = null;

        // if the field is not null than we don't want a new instance
        if (result == null) {
            // first check to see if this plain old field map has hints to the
            // actual type.
            if (fieldMap.getDestHintContainer() != null) {
                Class<?> destHintType = fieldMap.getDestHintType(srcFieldValue.getClass());
                // if the destType is null this means that there was more than
                // one hint.
                // we must have already set the destType then.
                if (destHintType != null) {
                    destFieldType = destHintType;
                }
            }
            // Check to see if explicit map-id has been specified for the field
            // mapping
            String mapId = fieldMap.getMapId();
                        
            Class<? extends Object> targetClass;
            if (fieldMap.getDestHintContainer() != null && fieldMap.getDestHintContainer().getHint() != null) {
                targetClass = fieldMap.getDestHintContainer().getHint();
            } else {
                targetClass = destFieldType;
            }
            
            classMap = getClassMap(srcFieldValue.getClass(), targetClass, mapId);

            result = DestBeanCreator.create(new BeanCreationDirective(srcFieldValue, classMap.getSrcClassToMap(),
                classMap.getDestClassToMap(), destFieldType, classMap.getDestClassBeanFactory(), classMap
                    .getDestClassBeanFactoryId(), fieldMap.getDestFieldCreateMethod() != null ? fieldMap
                    .getDestFieldCreateMethod() : classMap.getDestClassCreateMethod()));
        }

        map(classMap, srcFieldValue, result, false, fieldMap.getMapId());

        return result;
    }

    private Object mapCollection(Object srcObj, Object srcCollectionValue, FieldMap fieldMap, Object destObj) {
        // since we are mapping some sort of collection now is a good time to
        // decide
        // if they provided hints
        // if no hint is provided then we will use generics to determine the
        // mapping type
        if (fieldMap.getDestHintContainer() == null) {
            Class<?> genericType = fieldMap.getGenericType(destObj.getClass());
            if (genericType != null) {
                HintContainer destHintContainer = new HintContainer();
                destHintContainer.setHintName(genericType.getName());
                FieldMap cloneFieldMap = (FieldMap) fieldMap.clone();
                cloneFieldMap.setDestHintContainer(destHintContainer); // should
                // affect
                // only
                // this
                // time
                // as
                // fieldMap
                // is
                // cloned
                fieldMap = cloneFieldMap;
            }
        }

        // if it is an iterator object turn it into a List
        if (srcCollectionValue instanceof Iterator) {
            srcCollectionValue = IteratorUtils.toList((Iterator<?>) srcCollectionValue);
        }

        Class<?> destCollectionType = fieldMap.getDestFieldType(destObj.getClass());
        Class<?> srcFieldType = srcCollectionValue.getClass();
        Object result = null;

        // if they use a standard Collection we have to assume it is a
        // List...better
        // way to handle this?
        if (destCollectionType.getName().equals(Collection.class.getName())) {
            destCollectionType = List.class;
        }
        // Array to Array
        if (CollectionUtils.isArray(srcFieldType) && (CollectionUtils.isArray(destCollectionType))) {
            result = mapArrayToArray(srcObj, srcCollectionValue, fieldMap, destObj);
            // Array to List
        } else if (CollectionUtils.isArray(srcFieldType) && (CollectionUtils.isList(destCollectionType))) {
            result = mapArrayToList(srcObj, srcCollectionValue, fieldMap, destObj);
        }
        // List to Array
        else if (CollectionUtils.isList(srcFieldType) && (CollectionUtils.isArray(destCollectionType))) {
            result = mapListToArray(srcObj, (List<?>) srcCollectionValue, fieldMap, destObj);
            // List to List
        } else if (CollectionUtils.isList(srcFieldType) && (CollectionUtils.isList(destCollectionType))) {
            result = mapListToList(srcObj, (List<?>) srcCollectionValue, fieldMap, destObj);
        }
        // Set to Array
        else if (CollectionUtils.isSet(srcFieldType) && CollectionUtils.isArray(destCollectionType)) {
            result = mapSetToArray(srcObj, (Set<?>) srcCollectionValue, fieldMap, destObj);
        }
        // Array to Set
        else if (CollectionUtils.isArray(srcFieldType) && CollectionUtils.isSet(destCollectionType)) {
            result = addToSet(srcObj, fieldMap, Arrays.asList((Object[]) srcCollectionValue), destObj);
        }
        // Set to List
        else if (CollectionUtils.isSet(srcFieldType) && CollectionUtils.isList(destCollectionType)) {
            result = mapListToList(srcObj, (Set<?>) srcCollectionValue, fieldMap, destObj);
        }
        // Collection to Set
        else if (CollectionUtils.isCollection(srcFieldType) && CollectionUtils.isSet(destCollectionType)) {
            result = addToSet(srcObj, fieldMap, (Collection<?>) srcCollectionValue, destObj);
        }
        // List to Map value
        else if (CollectionUtils.isCollection(srcFieldType) && MappingUtils.isSupportedMap(destCollectionType)) {
            result = mapListToList(srcObj, (List<?>) srcCollectionValue, fieldMap, destObj);
        }
        return result;
    }

    private Object mapMap(Object srcObj, Map srcMapValue, FieldMap fieldMap, Object destObj) {
        Map result;
        Map destinationMap = (Map) fieldMap.getDestValue(destObj);
        if (destinationMap == null) {
            result = DestBeanCreator.create(srcMapValue.getClass());
        } else {
            result = destinationMap;
            if (fieldMap.isRemoveOrphans()) {
                result.clear();
            }
        }

        for (Entry<?, Object> srcEntry : ((Map<?, Object>) srcMapValue).entrySet()) {
            Object srcEntryValue = srcEntry.getValue();

            if (srcEntryValue == null) { // overwrites with null in any case
                result.put(srcEntry.getKey(), null);
                continue;
            }

            Object destEntryValue = mapOrRecurseObject(srcObj, srcEntryValue, srcEntryValue.getClass(), fieldMap,
                destObj);
            Object obj = result.get(srcEntry.getKey());
            if (obj != null && obj.equals(destEntryValue) && fieldMap.isNonCumulativeRelationship()) {
                map(null, srcEntryValue, obj, false, null);
            } else {
                result.put(srcEntry.getKey(), destEntryValue);
            }
        }
        return result;
    }

    private Object mapArrayToArray(Object srcObj, Object srcCollectionValue, FieldMap fieldMap, Object destObj) {
        Class destEntryType = fieldMap.getDestFieldType(destObj.getClass()).getComponentType();
        int size = Array.getLength(srcCollectionValue);
        if (CollectionUtils.isPrimitiveArray(srcCollectionValue.getClass())) {
            return addToPrimitiveArray(srcObj, fieldMap, size, srcCollectionValue, destObj, destEntryType);
        } else {
            List<?> list = Arrays.asList((Object[]) srcCollectionValue);
            List<?> returnList;
            if (!destEntryType.getName().equals(BASE_CLASS)) {
                returnList = addOrUpdateToList(srcObj, fieldMap, list, destObj, destEntryType);
            } else {
                returnList = addOrUpdateToList(srcObj, fieldMap, list, destObj, null);
            }
            return CollectionUtils.convertListToArray(returnList, destEntryType);
        }
    }

    private void mapFromIterateMethodFieldMap(Object srcObj, Object destObj, Object srcFieldValue, FieldMap fieldMapping) {
        // Iterate over the destFieldValue - iterating is fine unless we are
        // mapping
        // in the other direction.
        // Verify that it is truly a collection if it is an iterator object turn
        // it
        // into a List
        if (srcFieldValue instanceof Iterator) {
            srcFieldValue = IteratorUtils.toList((Iterator<?>) srcFieldValue);
        }
        if (srcFieldValue != null) {
            for (int i = 0; i < CollectionUtils.getLengthOfCollection(srcFieldValue); i++) {
                Object value = CollectionUtils.getValueFromCollection(srcFieldValue, i);
                // map this value
                if (fieldMapping.getDestHintContainer() == null) {
                    MappingUtils
                        .throwMappingException("<field type=\"iterate\"> must have a source or destination type hint");
                }
                // check for custom converters
                CustomConverter converter = MappingUtils.findCustomConverter(converterByDestTypeCache,
                    customConverterObjects, fieldMapping.getClassMap().getCustomConverters(), value.getClass(),
                    fieldMapping.getDestHintContainer().getHint());

                if (converter != null) {
                    Class<?> srcFieldClass = srcFieldValue.getClass();
                    value = mapUsingCustomConverterInstance(converter, srcFieldClass, value, fieldMapping
                        .getDestHintContainer().getHint(), null, fieldMapping, false);
                } else {
                    Object alreadyMappedValue = mappedFields.getMappedValue(value, fieldMapping.getDestHintContainer()
                        .getHint());
                    if (alreadyMappedValue != null) {
                        value = alreadyMappedValue;
                    } else {
                        value = map(value, fieldMapping.getDestHintContainer().getHint());
                    }
                }
                if (value != null) {
                    writeDestinationValue(destObj, value, fieldMapping, srcObj, null);
                }
            }
        }
        if (log.isDebugEnabled()) {
            log.debug(LogMsgFactory.createFieldMappingSuccessMsg(srcObj.getClass(), destObj.getClass(), fieldMapping
                .getSrcFieldName(), fieldMapping.getDestFieldName(), srcFieldValue, null, fieldMapping.getClassMap()
                .getMapId()));
        }
    }

    private Object addToPrimitiveArray(Object srcObj, FieldMap fieldMap, int size, Object srcCollectionValue,
        Object destObj, Class<?> destEntryType) {

        Object result;
        Object field = fieldMap.getDestValue(destObj);
        int arraySize = 0;
        if (field == null) {
            result = Array.newInstance(destEntryType, size);
        } else {
            result = Array.newInstance(destEntryType, size + Array.getLength(field));
            arraySize = Array.getLength(field);
            System.arraycopy(field, 0, result, 0, arraySize);
        }
        // primitive arrays are ALWAYS cumulative
        for (int i = 0; i < size; i++) {
            CopyByReferenceContainer copyByReferences = globalConfiguration.getCopyByReferences();
            Object toValue;
            if (srcCollectionValue != null && copyByReferences.contains(srcCollectionValue.getClass())) {
                toValue = srcCollectionValue;
            } else {
                toValue = mapOrRecurseObject(srcObj, Array.get(srcCollectionValue, i), destEntryType, fieldMap, destObj);
            }
            Array.set(result, arraySize, toValue);
            arraySize++;
        }
        return result;
    }

    private Object mapListToArray(Object srcObj, Collection<?> srcCollectionValue, FieldMap fieldMap, Object destObj) {
        Class destEntryType = fieldMap.getDestFieldType(destObj.getClass()).getComponentType();
        List list;
        if (!destEntryType.getName().equals(BASE_CLASS)) {
            list = addOrUpdateToList(srcObj, fieldMap, srcCollectionValue, destObj, destEntryType);
        } else {
            list = addOrUpdateToList(srcObj, fieldMap, srcCollectionValue, destObj);
        }
        return CollectionUtils.convertListToArray(list, destEntryType);
    }

    private List<?> mapListToList(Object srcObj, Collection<?> srcCollectionValue, FieldMap fieldMap, Object destObj) {
        return addOrUpdateToList(srcObj, fieldMap, srcCollectionValue, destObj);
    }

    private Set<?> addToSet(Object srcObj, FieldMap fieldMap, Collection<?> srcCollectionValue, Object destObj) {
        // create a list here so we can keep track of which elements we have
        // mapped, and remove all others if removeOrphans = true
        Set<Object> mappedElements = new HashSet<Object>();
        Class<?> destEntryType = null;

        LinkedHashSet<Object> result = new LinkedHashSet<Object>();
        // don't want to create the set if it already exists.
        Object field = fieldMap.getDestValue(destObj);
        if (field != null) {
            result.addAll((Collection<?>) field);
        }
        Object destValue;
        Class<?> prevDestEntryType = null;
        for (Object srcValue : srcCollectionValue) {
            if (destEntryType == null || (fieldMap.getDestHintContainer() != null && fieldMap.getDestHintContainer()
                .hasMoreThanOneHint())) {
                if (srcValue == null) {
                    destEntryType = prevDestEntryType;
                } else {
                    destEntryType = fieldMap.getDestHintType(srcValue.getClass());
                }
            }
            CopyByReferenceContainer copyByReferences = globalConfiguration.getCopyByReferences();
            if (srcValue != null && copyByReferences.contains(srcValue.getClass())) {
                destValue = srcValue;
            } else {
                destValue = mapOrRecurseObject(srcObj, srcValue, destEntryType, fieldMap, destObj);
            }
            prevDestEntryType = destEntryType;

            if (RelationshipType.NON_CUMULATIVE.equals(fieldMap.getRelationshipType()) && result.contains(destValue)) {
                List<Object> resultAsList = new ArrayList<Object>(result);
                int index = resultAsList.indexOf(destValue);
                // perform an update if complex type - can't map strings
                Object obj = resultAsList.get(index);
                // make sure it is not a String
                if (!obj.getClass().isAssignableFrom(String.class)) {
                    map(null, srcValue, obj, false, null);
                    mappedElements.add(obj);
                }
            } else {
                result.add(destValue);
                mappedElements.add(destValue);
            }
        }

        // If remove orphans - we only want to keep the objects we've mapped
        // from the src collection
        // so we'll clear result and replace all entries with the ones in
        // mappedElements
        if (fieldMap.isRemoveOrphans()) {
            result.clear();
            result.addAll(mappedElements);
        }

        if (field == null) {
            Class<? extends Set<?>> destSetType = (Class<? extends Set<?>>) fieldMap.getDestFieldType(destObj
                .getClass());
            return CollectionUtils.createNewSet(destSetType, result);
        } else {
            // Bug #1822421 - Clear first so we don't end up with the removed
            // orphans again
            ((Set) field).clear();
            ((Set) field).addAll(result);
            return (Set<?>) field;
        }
    }

    private List<?> addOrUpdateToList(Object srcObj, FieldMap fieldMap, Collection<?> srcCollectionValue,
        Object destObj, Class<?> destEntryType) {
        // create a Set here so we can keep track of which elements we have
        // mapped, and remove all others if removeOrphans = true
        List<Object> mappedElements = new ArrayList<Object>();
        List result;
        // don't want to create the list if it already exists.
        // these maps are special cases which do not fall under what we are
        // looking for
        Object field = fieldMap.getDestValue(destObj);
        result = prepareDestinationList(srcCollectionValue, field);

        Object destValue;
        Class<?> prevDestEntryType = null;
        for (Object srcValue : srcCollectionValue) {
            if (destEntryType == null || (fieldMap.getDestHintContainer() != null && fieldMap.getDestHintContainer()
                .hasMoreThanOneHint())) {
                if (srcValue == null) {
                    destEntryType = prevDestEntryType;
                } else {
                    destEntryType = fieldMap.getDestHintType(srcValue.getClass());
                }
            }
            CopyByReferenceContainer copyByReferences = globalConfiguration.getCopyByReferences();
            if (srcValue != null && copyByReferences.contains(srcValue.getClass())) {
                destValue = srcValue;
            } else {
                destValue = mapOrRecurseObject(srcObj, srcValue, destEntryType, fieldMap, destObj);
            }
            prevDestEntryType = destEntryType;

            if (RelationshipType.NON_CUMULATIVE.equals(fieldMap.getRelationshipType()) && result.contains(destValue)) {
                int index = result.indexOf(destValue);
                // perform an update if complex type - can't map strings
                Object obj = result.get(index);
                // make sure it is not a String
                if (obj != null && !obj.getClass().isAssignableFrom(String.class)) {
                    map(null, srcValue, obj, false, null);
                    mappedElements.add(obj);
                }
            } else {
                result.add(destValue);
                mappedElements.add(destValue);
            }

        }

        // If remove orphans - we only want to keep the objects we've mapped
        // from the src collection
        if (fieldMap.isRemoveOrphans()) {
            removeOrphans(mappedElements, result);
        }

        return result;
    }

    static void removeOrphans(Collection<?> mappedElements, List<Object> result) {
        for (Iterator<?> iterator = result.iterator(); iterator.hasNext();) {
            Object object = iterator.next();
            if (!mappedElements.contains(object)) {
                iterator.remove();
            }
        }
        for (Object object : mappedElements) {
            if (!result.contains(object)) {
                result.add(object);
            }
        }
    }

    static List<?> prepareDestinationList(Collection<?> srcCollectionValue, Object field) {
        if (field == null) {
            return new ArrayList<Object>(srcCollectionValue.size());
        } else {
            if (CollectionUtils.isList(field.getClass())) {
                return (List<?>) field;
            } else if (CollectionUtils.isArray(field.getClass())) {
                return new ArrayList<Object>(Arrays.asList((Object[]) field));
            } else { // assume it is neither - safest way is to create new List
                return new ArrayList<Object>(srcCollectionValue.size());
            }
        }
    }

    private List<?> addOrUpdateToList(Object srcObj, FieldMap fieldMap, Collection<?> srcCollectionValue, Object destObj) {
        return addOrUpdateToList(srcObj, fieldMap, srcCollectionValue, destObj, null);
    }

    private Object mapSetToArray(Object srcObj, Collection<?> srcCollectionValue, FieldMap fieldMap, Object destObj) {
        return mapListToArray(srcObj, srcCollectionValue, fieldMap, destObj);
    }

    private List<?> mapArrayToList(Object srcObj, Object srcCollectionValue, FieldMap fieldMap, Object destObj) {
        Class<?> destEntryType;
        if (fieldMap.getDestHintContainer() != null) {
            destEntryType = fieldMap.getDestHintContainer().getHint();
        } else {
            destEntryType = srcCollectionValue.getClass().getComponentType();
        }
        List<?> srcValueList;
        if (CollectionUtils.isPrimitiveArray(srcCollectionValue.getClass())) {
            srcValueList = CollectionUtils.convertPrimitiveArrayToList(srcCollectionValue);
        } else {
            srcValueList = Arrays.asList((Object[]) srcCollectionValue);
        }
        return addOrUpdateToList(srcObj, fieldMap, srcValueList, destObj, destEntryType);
    }

    private void writeDestinationValue(Object destObj, Object destFieldValue, FieldMap fieldMap, Object srcObj,
        Object defaultDestValue) {
        boolean bypass = false;

        if (destFieldValue == null && defaultDestValue != null) {
            destFieldValue = defaultDestValue;
        }

        // don't map null to dest field if map-null="false"
        if (destFieldValue == null && !fieldMap.isDestMapNull()) {
            bypass = true;
        }

        // don't map null to dest field if it is required
        //
        if (destFieldValue == null && fieldMap.isDestFieldRequired()) {
            // raise mapping exception if dest value is null but dest field is
            // required
            //
            throw new MappingException(String.format("Destination field '%s' cannot be null", fieldMap
                .getDestFieldCopy().getName()));
        }

        // don't map "" to dest field if map-empty-string="false"
        if (destFieldValue != null && !fieldMap.isDestMapEmptyString() && destFieldValue.getClass()
            .equals(String.class) && StringUtils.isEmpty((String) destFieldValue)) {
            bypass = true;
        }

        // trim string value if trim-strings="true"
        if (destFieldValue != null && fieldMap.isTrimStrings() && destFieldValue.getClass().equals(String.class)) {
            destFieldValue = ((String) destFieldValue).trim();
        }

        if (!bypass) {
            eventMgr.fireEvent(new DozerEvent(DozerEventType.MAPPING_PRE_WRITING_DEST_VALUE, fieldMap.getClassMap(),
                fieldMap, srcObj, destObj, destFieldValue));

            fieldMap.writeDestValue(destObj, destFieldValue);

            eventMgr.fireEvent(new DozerEvent(DozerEventType.MAPPING_POST_WRITING_DEST_VALUE, fieldMap.getClassMap(),
                fieldMap, srcObj, destObj, destFieldValue));
        }
    }

    private Object mapUsingCustomConverterInstance(CustomConverter converterInstance, Class<?> srcFieldClass,
        Object srcFieldValue, Class<?> destFieldClass, Object existingDestFieldValue, FieldMap fieldMap,
        boolean topLevel) {

        // skip original implementation to obtain the following: custom
        // converter should process null source value as usual
        //
        /*
         * // 1792048 - If map-null = "false" and src value is null, then don't
         * // even invoke custom converter if (srcFieldValue == null &&
         * !fieldMap.isDestMapNull()) { return null; }
         */
        long start = System.currentTimeMillis();

        if (converterInstance instanceof MapperAware) {
            ((MapperAware) converterInstance).setMapper(this);
        }

        // TODO Remove code duplication
        Object result;
        if (converterInstance instanceof ConfigurableCustomConverter) {
            ConfigurableCustomConverter theConverter = (ConfigurableCustomConverter) converterInstance;

            // Converter could be not configured for this particular case
            if (fieldMap != null) {
                String param = fieldMap.getCustomConverterParam();
                theConverter.setParameter(param);
            }

            // if this is a top level mapping the destObj is the highest level
            // mapping...not a recursive mapping
            if (topLevel) {
                result = theConverter.convert(existingDestFieldValue, srcFieldValue, destFieldClass, srcFieldClass);
            } else {
                Object existingValue = getExistingValue(fieldMap, existingDestFieldValue, destFieldClass);
                result = theConverter.convert(existingValue, srcFieldValue, destFieldClass, srcFieldClass);
            }
        } else {
            // if this is a top level mapping the destObj is the highest level
            // mapping...not a recursive mapping
            if (topLevel) {
                result = converterInstance
                    .convert(existingDestFieldValue, srcFieldValue, destFieldClass, srcFieldClass);
            } else {
                Object existingValue = getExistingValue(fieldMap, existingDestFieldValue, destFieldClass);
                result = converterInstance.convert(existingValue, srcFieldValue, destFieldClass, srcFieldClass);
            }
        }

        long stop = System.currentTimeMillis();
        statsMgr.increment(StatisticType.CUSTOM_CONVERTER_SUCCESS_COUNT);
        statsMgr.increment(StatisticType.CUSTOM_CONVERTER_TIME, stop - start);

        return result;
    }

    private Object mapUsingCustomConverter(Class<?> customConverterClass, Class<?> srcFieldClass, Object srcFieldValue,
        Class<?> destFieldClass, Object existingDestFieldValue, FieldMap fieldMap, boolean topLevel) {

        CustomConverter converterInstance = MappingUtils.findCustomConverterByClass(customConverterClass,
            customConverterObjects);

        return mapUsingCustomConverterInstance(converterInstance, srcFieldClass, srcFieldValue, destFieldClass,
            existingDestFieldValue, fieldMap, topLevel);
    }

    private Collection<ClassMap> checkForSuperTypeMapping(Class<?> srcClass, Class<?> destClass) {
        // Check cache first
        Object cacheKey = CacheKeyFactory.createKey(destClass, srcClass);
        Collection<ClassMap> cachedResult = (Collection<ClassMap>) superTypeCache.get(cacheKey);
        if (cachedResult != null) {
            return cachedResult;
        }

        // If no existing cache entry is found, determine super type mappings.
        // Recursively walk the inheritance hierarchy.
        List<ClassMap> superClasses = new ArrayList<ClassMap>();
        // Need to call getRealSuperclass because proxied data objects will not
        // return correct
        // superclass when using basic reflection

        List<Class<?>> superSrcClasses = MappingUtils.getSuperClassesAndInterfaces(srcClass);
        List<Class<?>> superDestClasses = MappingUtils.getSuperClassesAndInterfaces(destClass);

        // add the actual classes to check for mappings between the original and
        // the opposite
        // super classes
        superSrcClasses.add(0, srcClass);
        superDestClasses.add(0, destClass);

        for (Class<?> superSrcClass : superSrcClasses) {
            for (Class<?> superDestClass : superDestClasses) {
                if (!(superSrcClass.equals(srcClass) && superDestClass.equals(destClass))) {
                    checkForClassMapping(superSrcClass, superClasses, superDestClass);
                }
            }
        }

        Collections.reverse(superClasses); // Done so base classes are processed
        // first

        superTypeCache.put(cacheKey, superClasses);

        return superClasses;
    }

    private void checkForClassMapping(Class<?> srcClass, List<ClassMap> superClasses, Class<?> superDestClass) {
        ClassMap srcClassMap = classMappings.find(srcClass, superDestClass);
        if (srcClassMap != null) {
            superClasses.add(srcClassMap);
        }
    }

    /**
     * Maps classes using mappings of super classes.
     * 
     * @param superClasses mappings of super classes
     * @param srcObj source
     * @param destObj destination
     * @param mapId map id
     * @param overriddenFieldMappings overridden field mappings
     * @return list of mapped field keys
     */
    private List<String> processSuperTypeMapping(Collection<ClassMap> superClasses, Object srcObj, Object destObj,
        String mapId, List<String> overriddenFieldMappings) {
        List<String> mappedFieldKeys = new ArrayList<String>();

        for (ClassMap map : superClasses) {
            // create copy of super class map which will be modified farther
            ClassMap copy = map.copyOf();
            // remove from field mappings list entries that overridden by child class
            removeOverriddenFieldMappings(copy, overriddenFieldMappings, destObj);
            // map classes using field mappings for super classes
            map(copy, srcObj, destObj, true, mapId);
            for (FieldMap fieldMapping : copy.getFieldMaps()) {
                // remember mapped fields
                String key = MappingUtils.getMappedParentFieldKey(destObj, fieldMapping);
                mappedFieldKeys.add(key);
            }
        }
        
        return mappedFieldKeys;
    }

    /**
     * Removes field maps from class map what are overridden.
     * 
     * @param copy copy of class map
     * @param overriddenFieldMappings keys of overridden field mappings
     * @param destObj destination object
     */
    private void removeOverriddenFieldMappings(ClassMap copy, List<String> overriddenFieldMappings, Object destObj) {
        List<FieldMap> result = new ArrayList<FieldMap>();
        
        for(FieldMap fieldMap : copy.getFieldMaps()) {
            String key = MappingUtils.getMappedParentFieldKey(destObj, fieldMap);
            if (!overriddenFieldMappings.contains(key)) {
                result.add(fieldMap);
            }
        }
        
        copy.setFieldMaps(result);
    }

    private static Object getExistingValue(FieldMap fieldMap, Object destObj, Class<?> destFieldType) {
        // verify that the dest obj is not null
        if (destObj == null) {
            return null;
        }
        // call the getXX method to see if the field is already instantiated
        Object result = fieldMap.getDestValue(destObj);

        // When we are recursing through a list we need to make sure that we are
        // not
        // in the list
        // by checking the destFieldType
        if (result != null) {
            if (CollectionUtils.isList(result.getClass()) || CollectionUtils.isArray(result.getClass()) || CollectionUtils
                .isSet(result.getClass()) || MappingUtils.isSupportedMap(result.getClass())) {
                if (!CollectionUtils.isList(destFieldType) && !CollectionUtils.isArray(destFieldType) && !CollectionUtils
                    .isSet(destFieldType) && !MappingUtils.isSupportedMap(destFieldType)) {
                    // this means the getXX field is a List but we are actually
                    // trying to
                    // map one of its elements
                    result = null;
                }
            }
        }
        return result;
    }

    private ClassMap getClassMap(Class<?> srcClass, Class<?> destClass, String mapId) {
        ClassMap mapping = classMappings.find(srcClass, destClass, mapId);

        if (mapping == null) {
            // If mapping not found in existing custom mapping collection,
            // create
            // default as an explicit mapping must not
            // exist. The create default class map method will also add all
            // default
            // mappings that it can determine.
            mapping = ClassMapBuilder.createDefaultClassMap(globalConfiguration, srcClass, destClass);
            classMappings.addDefault(srcClass, destClass, mapping);
        }

        return mapping;
    }
    
}
