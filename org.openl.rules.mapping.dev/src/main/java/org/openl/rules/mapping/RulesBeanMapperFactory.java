package org.openl.rules.mapping;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.dozer.BeanFactory;
import org.dozer.CustomConverter;
import org.dozer.DozerEventListener;
import org.dozer.FieldMappingCondition;
import org.openl.rules.mapping.exception.RulesMappingException;
import org.openl.rules.mapping.validation.MappingBeanValidator;
import org.openl.rules.mapping.validation.OpenLDataBeanValidator;
import org.openl.rules.runtime.RulesEngineFactory;
import org.openl.runtime.AOpenLEngineFactory;
import org.openl.validation.IOpenLValidator;

/**
 * The factory class which provides methods to create mapper instance.
 */
public final class RulesBeanMapperFactory {

    private RulesBeanMapperFactory() {
    }

    /**
     * Creates mapper instance using file with mapping rule definitions.
     * 
     * @param source file with mapping rule definitions
     * @return mapper instance
     * @deprecated Use {@link #createMapperInstance(URL)}
     */
    @Deprecated
    public static Mapper createMapperInstance(File source) {
        return createMapperInstance(source, null, null);
    }

    /**
     * Creates mapper instance using file with mapping rule definitions.
     * 
     * @param source file with mapping rule definitions
     * @param customConvertersWithId external custom converters
     * @param conditionsWithId external conditions
     * @return mapper instance
     * @deprecated Use {@link #createMapperInstance(URL, Map, Map)}
     */
    @Deprecated
    public static Mapper createMapperInstance(File source,
            Map<String, CustomConverter> customConvertersWithId,
            Map<String, FieldMappingCondition> conditionsWithId) {
        return createMapperInstance(source, customConvertersWithId, conditionsWithId, null, null, true);
    }

    /**
     * Creates mapper instance using file with mapping rule definitions.
     * 
     * @param source file with mapping rule definitions
     * @param customConvertersWithId external custom converters
     * @param conditionsWithId external conditions
     * @param eventListeners dozer event listeners
     * @return mapper instance
     * @deprecated Use {@link #createMapperInstance(URL, Map, Map, List)}
     */
    @Deprecated
    public static Mapper createMapperInstance(File source,
            Map<String, CustomConverter> customConvertersWithId,
            Map<String, FieldMappingCondition> conditionsWithId,
            List<DozerEventListener> eventListeners) {
        return createMapperInstance(source, customConvertersWithId, conditionsWithId, null, eventListeners, true);
    }

    /**
     * Creates mapper instance using file with mapping rule definitions.
     *
     * @param source file with mapping rule definitions
     * @param customConvertersWithId external custom converters
     * @param conditionsWithId external conditions
     * @param factories custom bean factories
     * @return mapper instance
     * @deprecated Use {@link #createMapperInstance(URL, Map, Map, Map)}
     */
    @Deprecated
    public static Mapper createMapperInstance(File source,
            Map<String, CustomConverter> customConvertersWithId,
            Map<String, FieldMappingCondition> conditionsWithId,
            Map<String, BeanFactory> factories) {
        return createMapperInstance(source, customConvertersWithId, conditionsWithId, factories, null, true);
    }

    /**
     * Creates mapper instance using file with mapping rule definitions.
     *
     * @param source file with mapping rule definitions
     * @param customConvertersWithId external custom converters
     * @param conditionsWithId external conditions
     * @param factories custom bean factories
     * @param eventListeners dozer event listeners
     * @return mapper instance
     *
     * @deprecated Use {@link #createMapperInstance(URL, Map, Map, Map, List)}
     */
    @Deprecated
    public static Mapper createMapperInstance(File source,
            Map<String, CustomConverter> customConvertersWithId,
            Map<String, FieldMappingCondition> conditionsWithId,
            Map<String, BeanFactory> factories,
            List<DozerEventListener> eventListeners) {
        return createMapperInstance(source, customConvertersWithId, conditionsWithId, factories, eventListeners, true);
    }

    /**
     * Creates mapper instance using file with mapping rule definitions.
     * 
     * @param source file with mapping rule definitions
     * @param customConvertersWithId external custom converters
     * @param conditionsWithId external conditions
     * @param factories custom bean factories
     * @param eventListeners dozer event listeners
     * @param executionMode execution mode flag
     * @return mapper instance
     *
     * @deprecated Use {@link #createMapperInstance(URL, Map, Map, Map, List)}
     */
    @Deprecated
    public static Mapper createMapperInstance(File source,
            Map<String, CustomConverter> customConvertersWithId,
            Map<String, FieldMappingCondition> conditionsWithId,
            Map<String, BeanFactory> factories,
            List<DozerEventListener> eventListeners,
            boolean executionMode) {

        URL url;
        try {
            url = source.toURI().toURL();
        } catch (Exception e) {
            throw new RulesMappingException("Cannot get the URL for file: " + source.getAbsolutePath(), e);
        }
        return createMapperInstance(url, customConvertersWithId, conditionsWithId, factories, eventListeners, executionMode);
    }

    /**
     * Creates mapper instance using input stream with mapping rule definitions.
     *
     * @param source input stream with mapping rule definitions
     * @return mapper instance
     */
    public static Mapper createMapperInstance(URL source) {
        return createMapperInstance(source, null, null);
    }

    /**
     * Creates mapper instance using file with mapping rule definitions.
     * 
     * @param source input stream with mapping rule definitions
     * @param customConvertersWithId external custom converters
     * @param conditionsWithId external conditions
     * @return mapper instance
     */
    public static Mapper createMapperInstance(URL source,
            Map<String, CustomConverter> customConvertersWithId,
            Map<String, FieldMappingCondition> conditionsWithId) {
        return createMapperInstance(source, customConvertersWithId, conditionsWithId, null, null, true);
    }

    /**
     * Creates mapper instance using file with mapping rule definitions.
     * 
     * @param source input stream with mapping rule definitions
     * @param customConvertersWithId external custom converters
     * @param conditionsWithId external conditions
     * @param eventListeners dozer event listeners
     * @return mapper instance
     */
    public static Mapper createMapperInstance(URL source,
            Map<String, CustomConverter> customConvertersWithId,
            Map<String, FieldMappingCondition> conditionsWithId,
            List<DozerEventListener> eventListeners) {
        return createMapperInstance(source, customConvertersWithId, conditionsWithId, null, eventListeners, true);
    }

    /**
     * Creates mapper instance using file with mapping rule definitions.
     *
     * @param source file with mapping rule definitions
     * @param customConvertersWithId external custom converters
     * @param conditionsWithId external conditions
     * @param factories custom bean factories
     * @return mapper instance
     */
    public static Mapper createMapperInstance(URL source,
            Map<String, CustomConverter> customConvertersWithId,
            Map<String, FieldMappingCondition> conditionsWithId,
            Map<String, BeanFactory> factories) {
        return createMapperInstance(source, customConvertersWithId, conditionsWithId, factories, null, true);
    }

    /**
     * Creates mapper instance using file with mapping rule definitions.
     *
     * @param source file with mapping rule definitions
     * @param customConvertersWithId external custom converters
     * @param conditionsWithId external conditions
     * @param factories custom bean factories
     * @param eventListeners dozer event listeners
     * @return mapper instance
     */
    public static Mapper createMapperInstance(URL source,
            Map<String, CustomConverter> customConvertersWithId,
            Map<String, FieldMappingCondition> conditionsWithId,
            Map<String, BeanFactory> factories,
            List<DozerEventListener> eventListeners) {
        return createMapperInstance(source, customConvertersWithId, conditionsWithId, factories, eventListeners, true);
    }

    /**
     * Creates mapper instance using input stream with mapping rule definitions.
     *
     * @param source input stream with mapping rule definitions
     * @param customConvertersWithId external custom converters
     * @param conditionsWithId external conditions
     * @param factories custom bean factories
     * @param eventListeners dozer event listeners
     * @param executionMode execution mode flag
     * @return mapper instance
     * @deprecated Use {@link #createMapperInstance(URL, Map, Map, Map, List)}
     */
    @Deprecated
    public static Mapper createMapperInstance(URL source,
            Map<String, CustomConverter> customConvertersWithId,
            Map<String, FieldMappingCondition> conditionsWithId,
            Map<String, BeanFactory> factories,
            List<DozerEventListener> eventListeners,
            boolean executionMode) {

        try {
            RulesEngineFactory factory = initEngine(source, executionMode);

            Class<?> instanceClass = factory.getInterfaceClass();
            Object instance = factory.newInstance();

            // Check that compilation process completed successfully.
            factory.getCompiledOpenClass().throwErrorExceptionsIfAny();

            // Get OpenL configuration object. The OpenL configuration object is
            // created by OpenL engine during compilation process and contains
            // information about imported types. We should use it to obtain
            // required types because if user defined, for example, convert
            // method as an external java static method and didn't use package
            // name (e.g. MyClass.myConvertMethod) we will not have enough
            // information to get convert method.
            //
            TypeResolver typeResolver;
            if (executionMode) {
                typeResolver = OpenLReflectionUtils.getTypeResolver(factory.getSourceCode().getUri(), factory.getUserContext());
            } else {
                typeResolver = OpenLReflectionUtils.getTypeResolver(factory.getCompiledOpenClass().getOpenClass(), factory.getUserContext());
            }

            return new RulesBeanMapper(instanceClass,
                instance,
                typeResolver,
                customConvertersWithId,
                conditionsWithId,
                factories,
                eventListeners);
        } catch (Exception e) {
            throw new RulesMappingException("Cannot load mapping definitions from the URL: " + source, e);
        }
    }

    /**
     * Initializes OpenL engine.
     * 
     * @param source OpenL project source file
     * @param executionMode execution mode flag
     * @return rules engine instance
     *
     * @deprecated No replacement. Candidate for deletion.
     */
    @Deprecated
    public static RulesEngineFactory initEngine(URL source, boolean executionMode) {

        RulesEngineFactory factory = new RulesEngineFactory(source);
        factory.setExecutionMode(executionMode);

        if (!executionMode) {
            registerTypeValidator(factory, new MappingBeanValidator(factory.getUserContext()));
        }

        return factory;
    }

    private synchronized static void registerTypeValidator(AOpenLEngineFactory factory,
            OpenLDataBeanValidator<?> validator) {

        for (IOpenLValidator regValidator : factory.getOpenL().getCompileContext().getValidators()) {
            if (regValidator.getClass() == validator.getClass()) {
                return;
            }
        }
        factory.getOpenL().getCompileContext().addValidator(validator);
    }
}
