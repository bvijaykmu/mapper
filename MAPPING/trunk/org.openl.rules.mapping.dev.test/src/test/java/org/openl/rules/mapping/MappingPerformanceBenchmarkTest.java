package org.openl.rules.mapping;

import java.io.File;
import java.io.FileReader;

import org.junit.Test;
import org.openl.rules.mapping.RulesBeanMapper;
import org.openl.rules.mapping.RulesBeanMapperFactory;
import org.openl.rules.mapping.to.model1.PolicyEntity;
import org.openl.rules.mapping.to.model2.Policy;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

public class MappingPerformanceBenchmarkTest {

    @Test
    public void performaceTest() throws Exception {

        XStream xstream = new XStream(new DomDriver());
        
        File data = new File("src/test/resources/org/openl/rules/mapping/xml/policy.xml");
        PolicyEntity policyEntity = (PolicyEntity) xstream.fromXML(new FileReader(data));
        
        File source = new File("src/test/resources/org/openl/rules/mapping/mapping.xlsx");
        
        long time1 = System.currentTimeMillis();
        RulesBeanMapper mapper = RulesBeanMapperFactory.createMapperInstance(source);
        long time2 = System.currentTimeMillis();

        mapper.map(policyEntity, Policy.class);
        
        long time3 = System.currentTimeMillis();

        mapper.map(policyEntity, Policy.class);
        
        long time4 = System.currentTimeMillis();

        System.out.println("Init time: " + (time2 - time1) + "ms");
        System.out.println("Map time (first invocation): " + (time3 - time2) + "ms");
        System.out.println("Map time (second invocation): " + (time4 - time3) + "ms");
    }

}
