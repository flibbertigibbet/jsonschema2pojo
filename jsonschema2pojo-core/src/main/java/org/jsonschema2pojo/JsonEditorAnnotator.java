package org.jsonschema2pojo;

import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.codemodel.JAnnotationArrayMember;
import com.sun.codemodel.JDefinedClass;

/**
 * Created by kat on 11/21/15.
 */
public class JsonEditorAnnotator extends AbstractAnnotator {

    // as used by json-editor
    static int DEFAULT_PROPERTY_ORDER = 1000;

    @Override
    public void propertyOrder(JDefinedClass clazz, JsonNode propertiesNode) {
        JAnnotationArrayMember annotationValue = clazz.annotate(JsonPropertyOrder.class).paramArray("value");

        SortedMap<Integer, String> fieldOrders = new TreeMap();

        for (Iterator<String> properties = propertiesNode.fieldNames(); properties.hasNext();) {
            //annotationValue.param(properties.next());
            String propertyName = properties.next();
            JsonNode element = propertiesNode.findValue(propertyName);
            JsonNode propertyOrder = element.findValue("propertyOrder");

            int order = DEFAULT_PROPERTY_ORDER;
            if (propertyOrder != null) {
                order = propertyOrder.asInt(DEFAULT_PROPERTY_ORDER);
            }

            fieldOrders.put(order, propertyName);
        }

        for (String name: fieldOrders.values()) {
            annotationValue.param(name);
        }
    }

    @Override
    public boolean isAdditionalPropertiesSupported() {
        return true;
    }
}
