package org.openl.rules.mapping;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Transformer;
import org.apache.commons.lang3.StringUtils;
import org.dozer.BeanFactory;
import org.dozer.CustomConverter;
import org.dozer.DozerBeanMapper;
import org.dozer.DozerEventListener;
import org.dozer.FieldMappingCondition;
import org.openl.conf.IOpenLConfiguration;
import org.openl.conf.OpenLConfiguration;
import org.openl.rules.mapping.definition.BeanMap;
import org.openl.rules.mapping.definition.BeanMapConfiguration;
import org.openl.rules.mapping.definition.Configuration;
import org.openl.rules.mapping.definition.ConverterDescriptor;
import org.openl.rules.mapping.exception.RulesMappingException;
import org.openl.rules.mapping.loader.RulesMappingsLoader;
import org.openl.rules.mapping.loader.dozer.DozerBuilder;
import org.openl.rules.runtime.RulesEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The factory class which provides methods to create mapper instance.
 */
public final class RulesBeanMapperFactory {

    private static final Logger LOG = LoggerFactory.getLogger(RulesBeanMapperFactory.class);

    private RulesBeanMapperFactory() {
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
        return createMapperInstance(source, customConvertersWithId, conditionsWithId, null, null);
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
        return createMapperInstance(source, customConvertersWithId, conditionsWithId, null, eventListeners);
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
        return createMapperInstance(source, customConvertersWithId, conditionsWithId, factories, null);
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

        try {

            RulesEngineFactory factory1 = new RulesEngineFactory(source);
            factory1.setExecutionMode(true);

            RulesEngineFactory factory = factory1;

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
            String name = "org.openl.rules.java::" + factory.getSourceCode().getUri();
            IOpenLConfiguration config = OpenLConfiguration.getInstance(name, factory.getUserContext());

            typeResolver = config != null ? new RulesTypeResolver(config) : null;

            org.dozer.Mapper dozerMapper = init(instanceClass, instance, typeResolver, customConvertersWithId, conditionsWithId, factories, eventListeners);
            return new MappingProxy(dozerMapper);
        } catch (Exception e) {
            throw new RulesMappingException("Cannot load mapping definitions from the URL: " + source, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static org.dozer.Mapper init(Class<?> instanceClass,
                                         Object instance,
                                         TypeResolver typeResolver,
                                         Map<String, CustomConverter> customConvertersWithId,
                                         Map<String, FieldMappingCondition> conditionsWithId,
                                         Map<String, BeanFactory> factories,
                                         List<DozerEventListener> eventListeners) {

        RulesMappingsLoader mappingsLoader = new RulesMappingsLoader(instanceClass, instance, typeResolver);
        DozerBuilder dozerBuilder = new DozerBuilder();
        dozerBuilder.mappingBuilder().customConvertersWithId(customConvertersWithId);
        dozerBuilder.mappingBuilder().conditionsWithId(conditionsWithId);

        dozerBuilder.mappingBuilder().eventListeners(eventListeners);

        Collection<ConverterDescriptor> defaultConverters = mappingsLoader.loadDefaultConverters();

        for (ConverterDescriptor converter : defaultConverters) {
            dozerBuilder.configBuilder().defaultConverter(converter);
        }

        if (LOG.isDebugEnabled()) {
            Collection<String> defaultConverterLogEntries = CollectionUtils.collect(defaultConverters,
                    new Transformer() {

                        @Override
                        public String transform(Object arg) {
                            ConverterDescriptor descriptor = (ConverterDescriptor) arg;
                            return descriptor.getConverterId();
                        }

                    });

            LOG.debug("Default converters:\n" + StringUtils.join(defaultConverterLogEntries, "\n"));
            LOG.debug("External converters: " + StringUtils.join(customConvertersWithId == null ? new ArrayList<Object>(0)
                            : customConvertersWithId.keySet(),
                    ", "));
            LOG.debug("External conditions: " + StringUtils.join(conditionsWithId == null ? new ArrayList<Object>(0)
                            : conditionsWithId.keySet(),
                    ", "));
        }

        Configuration globalConfiguration = mappingsLoader.loadConfiguration();

        dozerBuilder.configBuilder().dateFormat(globalConfiguration.getDateFormat());
        dozerBuilder.configBuilder().wildcard(globalConfiguration.isWildcard());
        dozerBuilder.configBuilder().trimStrings(globalConfiguration.isTrimStrings());
        dozerBuilder.configBuilder().mapNulls(globalConfiguration.isMapNulls());
        dozerBuilder.configBuilder().mapEmptyStrings(globalConfiguration.isMapEmptyStrings());
        dozerBuilder.configBuilder().requiredFields(globalConfiguration.isRequiredFields());
        dozerBuilder.configBuilder().beanFactory(globalConfiguration.getBeanFactory());

        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("Global configuration: dateFormat=%s, wildcard=%s, trimStrings=%s, mapNulls=%s, mapEmptyStrings=%s, requiredFields=%s, beanFactory=%s",
                    globalConfiguration.getDateFormat(),
                    globalConfiguration.isWildcard(),
                    globalConfiguration.isTrimStrings(),
                    globalConfiguration.isMapNulls(),
                    globalConfiguration.isMapEmptyStrings(),
                    globalConfiguration.isRequiredFields(),
                    globalConfiguration.getBeanFactory()));
        }

        Map<String, BeanMapConfiguration> mappingConfigurations = mappingsLoader.loadBeanMapConfiguraitons(globalConfiguration);

        if (LOG.isDebugEnabled()) {
            Collection<String> beanConfigLogEntries = CollectionUtils.collect(mappingConfigurations.values(),
                    new Transformer() {

                        @Override
                        public String transform(Object arg) {
                            BeanMapConfiguration conf = (BeanMapConfiguration) arg;
                            return String.format("[classA=%s, classB=%s, classABeanFactory=%s, classBBeanFactory=%s, mapNulls=%s, mapEmptyStrings=%s, trimStrings=%s, requiredFields=%s, wildcard=%s, dateFormat=%s]",
                                    conf.getClassA(),
                                    conf.getClassB(),
                                    conf.getClassABeanFactory(),
                                    conf.getClassBBeanFactory(),
                                    conf.isMapNulls(),
                                    conf.isMapEmptyStrings(),
                                    conf.isTrimStrings(),
                                    conf.isRequiredFields(),
                                    conf.isWildcard(),
                                    conf.getDateFormat());
                        }
                    });

            LOG.debug("Bean level configurations:\n" + StringUtils.join(beanConfigLogEntries, "\n"));
        }

        Collection<BeanMap> mappings = mappingsLoader.loadMappings(mappingConfigurations, globalConfiguration);

        for (BeanMap mapping : mappings) {
            dozerBuilder.mappingBuilder().mapping(mapping);
        }

        DozerBeanMapper beanMapper = dozerBuilder.buildMapper();

        if (factories != null) {
            beanMapper.setFactories(factories);
        }
        return beanMapper;
    }
}
