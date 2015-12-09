/**
 * Copyright ¬© 2010-2014 Nokia
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

package org.jsonschema2pojo.rules;

import static com.sun.codemodel.JExpr.FALSE;
import static com.sun.codemodel.JExpr._new;
import static com.sun.codemodel.JExpr._super;
import static com.sun.codemodel.JExpr._this;
import static com.sun.codemodel.JExpr.cast;
import static com.sun.codemodel.JExpr.invoke;
import static com.sun.codemodel.JExpr.lit;
import static com.sun.codemodel.JMod.FINAL;
import static com.sun.codemodel.JMod.PROTECTED;
import static com.sun.codemodel.JMod.PUBLIC;
import static com.sun.codemodel.JMod.STATIC;

import java.util.Iterator;

import org.jsonschema2pojo.Schema;
import org.jsonschema2pojo.util.LanguageFeatures;
import org.jsonschema2pojo.util.Models;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JConditional;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldRef;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JOp;
import com.sun.codemodel.JSwitch;
import com.sun.codemodel.JType;
import com.sun.codemodel.JTypeVar;
import com.sun.codemodel.JVar;

/**
 * Adds methods for dynamically getting, setting, and building properties.
 *
 * @author Christian Trimble
 *
 */
public class DynamicPropertiesRule implements Rule<JDefinedClass, JDefinedClass> {

    public static final String NOT_FOUND_VALUE_FIELD = "NOT_FOUND_VALUE";
    public static final String SETTER_NAME = "set";
    public static final String GETTER_NAME = "get";
    public static final String BUILDER_NAME = "with";
    public static final String DEFINED_SETTER_NAME = "declaredProperty";
    public static final String DEFINED_GETTER_NAME = "declaredPropertyOrNotFound";

    private RuleFactory ruleFactory;

    public DynamicPropertiesRule(RuleFactory ruleFactory) {
        this.ruleFactory = ruleFactory;
    }

    /**
     * This rule adds dynamic getter, setter and builder methods based on the properties and additional properties
     * defined in a schema.
     * <p>
     * If accessors are being generated, then methods for getting and setting properties by name will be added.  These
     * methods first attempt to call the appropriate getter or setter for the property.  If the named property is not defined,
     * then the additional properties map is used.
     * <p>
     * If builders are being generated, then a method for building properties by name will be added.  This method first
     * attempts to call the builder for the property.  If no property with the supplied name is defined, then the additional
     * properties map is used.
     * <p>
     * The methods generated by this class throw an IllegalArgumentException, if the name specified for the property is unknown and
     * additional properties are not enabled.  A ClassCastException will be thrown, when the value being set is incompatible with the
     * type of the named property.
     * 
     * @param nodeName
     *            the name of the node for which dynamic getters, setters, and builders are being added.
     * @param node
     *            the properties node, containing property names and their
     *            definition
     * @param jclass
     *            the Java type which will have the given properties added
     * @param currentSchema
     *            the schema being implemented
     * @return the given jclass
     */
    @Override
    public JDefinedClass apply(String nodeName, JsonNode node, JDefinedClass jclass, Schema currentSchema) {
        if (!ruleFactory.getGenerationConfig().isIncludeDynamicAccessors()) {
            return jclass;
        }

        if (ruleFactory.getGenerationConfig().isIncludeAccessors() ||
                ruleFactory.getGenerationConfig().isGenerateBuilders()) {
            if (LanguageFeatures.canUseJava7(ruleFactory.getGenerationConfig())) {
                addInternalSetMethodJava7(jclass, node, currentSchema);
                addInternalGetMethodJava7(jclass, node, currentSchema);
            } else {
                addInternalSetMethodJava6(jclass, node, currentSchema);
                addInternalGetMethodJava6(jclass, node, currentSchema);
            }
        }

        if (ruleFactory.getGenerationConfig().isIncludeAccessors()) {
            addGetMethods(jclass, node, currentSchema);
            addSetMethods(jclass, node, currentSchema);
        }

        if (ruleFactory.getGenerationConfig().isGenerateBuilders()) {
            addWithMethods(jclass, node, currentSchema);
        }

        return jclass;
    }

    void addGetMethods(JDefinedClass jclass, JsonNode propertiesNode, Schema currentSchema) {
        JFieldRef notFoundVar = getOrAddNotFoundVar(jclass);
        JMethod internalGetMethod = this.getInternalGetMethod(jclass, propertiesNode, currentSchema);
        addPublicGetMethod(jclass, propertiesNode, internalGetMethod, notFoundVar);
    }

    JFieldRef getOrAddNotFoundVar(JDefinedClass jclass) {
        jclass.field(PROTECTED | STATIC | FINAL, Object.class, NOT_FOUND_VALUE_FIELD,
                _new(jclass.owner()._ref(Object.class)));
        return jclass.staticRef(NOT_FOUND_VALUE_FIELD);
    }

    private JMethod addPublicGetMethod(JDefinedClass jclass, JsonNode propertiesNode, JMethod internalGetMethod, JFieldRef notFoundValue) {
        JMethod method = jclass.method(PUBLIC, jclass.owner()._ref(Object.class), GETTER_NAME);
        JTypeVar returnType = method.generify("T");
        method.type(returnType);
        Models.suppressWarnings(method, "unchecked");
        JVar nameParam = method.param(String.class, "name");
        JBlock body = method.body();
        JVar valueVar = body.decl(jclass.owner()._ref(Object.class), "value",
                invoke(internalGetMethod).arg(nameParam).arg(notFoundValue));
        JConditional found = method.body()._if(notFoundValue.ne(valueVar));
        found._then()._return(cast(returnType, valueVar));
        JBlock notFound = found._else();

        JMethod getAdditionalProperties = jclass.getMethod("getAdditionalProperties", new JType[] {});
        if (getAdditionalProperties != null) {
            notFound._return(cast(returnType, invoke(getAdditionalProperties).invoke("get").arg(nameParam)));
        } else {
            notFound._throw(illegalArgumentInvocation(jclass, nameParam));
        }

        return method;
    }

    private JMethod addInternalGetMethodJava7(JDefinedClass jclass, JsonNode propertiesNode, Schema currentSchema) {
        JMethod method = jclass.method(PROTECTED, jclass.owner()._ref(Object.class), DEFINED_GETTER_NAME);
        JVar nameParam = method.param(String.class, "name");
        JVar notFoundParam = method.param(jclass.owner()._ref(Object.class), "notFoundValue");
        Models.suppressWarnings(method, "unchecked");
        JBlock body = method.body();
        JSwitch propertySwitch = body._switch(nameParam);
        if (propertiesNode != null) {
            for (Iterator<String> properties = propertiesNode.fieldNames(); properties.hasNext();) {
                String propertyName = properties.next();
                String fieldName = ruleFactory.getNameHelper().getPropertyName(propertyName);
                JType propertyType = jclass.fields().get(fieldName).type();

                addGetPropertyCase(jclass, propertySwitch, propertyName, propertyType);
            }
        }
        JClass extendsType = jclass._extends();
        if (extendsType != null && extendsType instanceof JDefinedClass) {
            JDefinedClass parentClass = (JDefinedClass) extendsType;
            JMethod parentMethod = parentClass.getMethod(DEFINED_GETTER_NAME,
                    new JType[] { parentClass.owner()._ref(String.class), parentClass.owner()._ref(Object.class) });
            propertySwitch._default().body()
                    ._return(_super().invoke(parentMethod).arg(nameParam).arg(notFoundParam));
        } else {
            propertySwitch._default().body()
                    ._return(notFoundParam);
        }

        return method;
    }

    private JMethod addInternalGetMethodJava6(JDefinedClass jclass, JsonNode propertiesNode, Schema currentSchema) {
        JMethod method = jclass.method(PROTECTED, jclass.owner()._ref(Object.class), DEFINED_GETTER_NAME);
        JVar nameParam = method.param(String.class, "name");
        JVar notFoundParam = method.param(jclass.owner()._ref(Object.class), "notFoundValue");
        Models.suppressWarnings(method, "unchecked");
        JBlock body = method.body();
        JConditional propertyConditional = null;
        if (propertiesNode != null) {
            for (Iterator<String> properties = propertiesNode.fieldNames(); properties.hasNext();) {
                String propertyName = properties.next();
                String fieldName = ruleFactory.getNameHelper().getPropertyName(propertyName);
                JType propertyType = jclass.fields().get(fieldName).type();

                JExpression condition = JExpr.lit(propertyName).invoke("equals").arg(nameParam);
                if (propertyConditional == null) {
                    propertyConditional = body._if(condition);
                } else {
                    propertyConditional = propertyConditional._elseif(condition);
                }
                JMethod propertyGetter = jclass.getMethod(getGetterName(propertyName, propertyType), new JType[] {});
                propertyConditional._then()._return(invoke(propertyGetter));
            }
        }
        JClass extendsType = jclass._extends();
        JBlock lastBlock = propertyConditional == null ? body : propertyConditional._else();
        if (extendsType != null && extendsType instanceof JDefinedClass) {
            JDefinedClass parentClass = (JDefinedClass) extendsType;
            JMethod parentMethod = parentClass.getMethod(DEFINED_GETTER_NAME,
                    new JType[] { parentClass.owner()._ref(String.class), parentClass.owner()._ref(Object.class) });
            lastBlock._return(_super().invoke(parentMethod).arg(nameParam).arg(notFoundParam));
        } else {
            lastBlock._return(notFoundParam);
        }

        return method;
    }

    private void addGetPropertyCase(JDefinedClass jclass, JSwitch propertySwitch, String propertyName, JType propertyType) {
        JMethod propertyGetter = jclass.getMethod(getGetterName(propertyName, propertyType), new JType[] {});
        propertySwitch._case(JExpr.lit(propertyName)).body()
                ._return(invoke(propertyGetter));
    }

    private void addSetMethods(JDefinedClass jclass, JsonNode propertiesNode, Schema currentSchema) {
        JMethod internalSetMethod = getInternalSetMethod(jclass, propertiesNode, currentSchema);
        addPublicSetMethod(jclass, propertiesNode, internalSetMethod);
    }

    private JMethod addPublicSetMethod(JDefinedClass jclass, JsonNode propertiesNode, JMethod internalSetMethod) {
        JMethod method = jclass.method(PUBLIC, jclass.owner().VOID, SETTER_NAME);
        JVar nameParam = method.param(String.class, "name");
        JVar valueParam = method.param(Object.class, "value");
        Models.suppressWarnings(method, "unchecked");
        JBlock body = method.body();
        JBlock notFound = body._if(JOp.not(invoke(internalSetMethod).arg(nameParam).arg(valueParam)))._then();

        // if we have additional properties, then put value.
        JMethod getAdditionalProperties = jclass.getMethod("getAdditionalProperties", new JType[] {});
        if (getAdditionalProperties != null) {
            JType additionalPropertiesType = ((JClass) (getAdditionalProperties.type())).getTypeParameters().get(1);
            notFound.add(invoke(getAdditionalProperties).invoke("put").arg(nameParam)
                    .arg(cast(additionalPropertiesType, valueParam)));
        }
        // else throw exception.
        else {
            notFound._throw(illegalArgumentInvocation(jclass, nameParam));
        }

        return method;
    }

    private void addWithMethods(JDefinedClass jclass, JsonNode propertiesNode, Schema currentSchema) {
        JMethod internalSetMethod = getInternalSetMethod(jclass, propertiesNode, currentSchema);
        addPublicWithMethod(jclass, propertiesNode, internalSetMethod);
    }

    private JMethod addPublicWithMethod(JDefinedClass jclass, JsonNode propertiesNode, JMethod internalSetMethod) {
        JMethod method = jclass.method(PUBLIC, jclass, BUILDER_NAME);
        JVar nameParam = method.param(String.class, "name");
        JVar valueParam = method.param(Object.class, "value");
        Models.suppressWarnings(method, "unchecked");
        JBlock body = method.body();
        JBlock notFound = body._if(JOp.not(invoke(internalSetMethod).arg(nameParam).arg(valueParam)))._then();

        // if we have additional properties, then put value.
        JMethod getAdditionalProperties = jclass.getMethod("getAdditionalProperties", new JType[] {});
        if (getAdditionalProperties != null) {
            JType additionalPropertiesType = ((JClass) (getAdditionalProperties.type())).getTypeParameters().get(1);
            notFound.add(invoke(getAdditionalProperties).invoke("put").arg(nameParam)
                    .arg(cast(additionalPropertiesType, valueParam)));
        }
        // else throw exception.
        else {
            notFound._throw(illegalArgumentInvocation(jclass, nameParam));
        }
        body._return(_this());

        return method;
    }

    private JMethod addInternalSetMethodJava7(JDefinedClass jclass, JsonNode propertiesNode, Schema currentSchema) {
        JMethod method = jclass.method(PROTECTED, jclass.owner().BOOLEAN, DEFINED_SETTER_NAME);
        JVar nameParam = method.param(String.class, "name");
        JVar valueParam = method.param(Object.class, "value");
        Models.suppressWarnings(method, "unchecked");
        JBlock body = method.body();
        JSwitch propertySwitch = body._switch(nameParam);
        if (propertiesNode != null) {
            for (Iterator<String> properties = propertiesNode.fieldNames(); properties.hasNext();) {
                String propertyName = properties.next();
                String fieldName = ruleFactory.getNameHelper().getPropertyName(propertyName);
                JType propertyType = jclass.fields().get(fieldName).type();

                addSetPropertyCase(jclass, propertySwitch, propertyName, propertyType, valueParam);
            }
        }
        JBlock defaultBlock = propertySwitch._default().body();
        JClass extendsType = jclass._extends();
        if (extendsType != null && extendsType instanceof JDefinedClass) {
            JDefinedClass parentClass = (JDefinedClass) extendsType;
            JMethod parentMethod = parentClass.getMethod(DEFINED_SETTER_NAME,
                    new JType[] { parentClass.owner()._ref(String.class), parentClass.owner()._ref(Object.class) });
            defaultBlock._return(_super().invoke(parentMethod).arg(nameParam).arg(valueParam));
        } else {
            defaultBlock._return(FALSE);
        }
        return method;
    }

    private JMethod addInternalSetMethodJava6(JDefinedClass jclass, JsonNode propertiesNode, Schema currentSchema) {
        JMethod method = jclass.method(PROTECTED, jclass.owner().BOOLEAN, DEFINED_SETTER_NAME);
        JVar nameParam = method.param(String.class, "name");
        JVar valueParam = method.param(Object.class, "value");
        Models.suppressWarnings(method, "unchecked");
        JBlock body = method.body();
        JConditional propertyConditional = null;
        if (propertiesNode != null) {
            for (Iterator<String> properties = propertiesNode.fieldNames(); properties.hasNext();) {
                String propertyName = properties.next();
                String fieldName = ruleFactory.getNameHelper().getPropertyName(propertyName);
                JType propertyType = jclass.fields().get(fieldName).type();
                JExpression condition = JExpr.lit(propertyName).invoke("equals").arg(nameParam);
                propertyConditional = propertyConditional == null ? propertyConditional = body._if(condition)
                        : propertyConditional._elseif(condition);

                JBlock callSite = propertyConditional._then();
                addSetProperty(jclass, callSite, propertyName, propertyType, valueParam);
                callSite._return(JExpr.TRUE);
            }
        }
        JClass extendsType = jclass._extends();
        JBlock lastBlock = propertyConditional == null ? body : propertyConditional._else();

        if (extendsType != null && extendsType instanceof JDefinedClass) {
            JDefinedClass parentClass = (JDefinedClass) extendsType;
            JMethod parentMethod = parentClass.getMethod(DEFINED_SETTER_NAME,
                    new JType[] { parentClass.owner()._ref(String.class), parentClass.owner()._ref(Object.class) });
            lastBlock._return(JExpr._super().invoke(parentMethod).arg(nameParam).arg(valueParam));
        } else {
            lastBlock._return(FALSE);
        }
        return method;
    }

    private JMethod getInternalSetMethod(JDefinedClass jclass, JsonNode propertiesNode, Schema currentSchema) {
        return jclass.getMethod(DEFINED_SETTER_NAME,
                new JType[] { jclass.owner().ref(String.class), jclass.owner().ref(Object.class) });
    }

    private JMethod getInternalGetMethod(JDefinedClass jclass, JsonNode propertiesNode, Schema currentSchema) {
        return jclass.getMethod(DEFINED_GETTER_NAME,
                new JType[] { jclass.owner().ref(String.class), jclass.owner().ref(Object.class) });
    }

    private void addSetPropertyCase(JDefinedClass jclass, JSwitch setterSwitch, String propertyName, JType propertyType, JVar valueVar) {
        JBlock setterBody = setterSwitch._case(lit(propertyName)).body();
        addSetProperty(jclass, setterBody, propertyName, propertyType, valueVar);
        setterBody._return(JExpr.TRUE);
    }

    private void addSetProperty(JDefinedClass jclass, JBlock callSite, String propertyName, JType propertyType, JVar valueVar) {
        JMethod propertySetter = jclass.getMethod(getSetterName(propertyName), new JType[] { propertyType });
        JConditional isInstance = callSite._if(valueVar._instanceof(propertyType.boxify().erasure()));
        isInstance._then()
                .invoke(propertySetter).arg(cast(propertyType.boxify(), valueVar));
        isInstance._else()
                ._throw(illegalArgumentInvocation(jclass, propertyName, propertyType, valueVar));
    }

    private JInvocation illegalArgumentInvocation(JDefinedClass jclass, JVar propertyName) {
        return _new(jclass.owner()._ref(IllegalArgumentException.class))
                .arg(lit("property \"").plus(propertyName).plus(lit("\" is not defined")));
    }

    private JInvocation illegalArgumentInvocation(JDefinedClass jclass, String propertyName, JType propertyType, JVar valueVar) {
        return _new(jclass.owner()._ref(IllegalArgumentException.class))
                .arg(lit("property \"" + propertyName + "\" is of type \"" + propertyType.fullName() + "\", but got ")
                        .plus(valueVar.invoke("getClass").invoke("toString")));
    }

    private String getSetterName(String propertyName) {
        return ruleFactory.getNameHelper().getSetterName(propertyName);
    }

    private String getGetterName(String propertyName, JType type) {
        return ruleFactory.getNameHelper().getGetterName(propertyName, type);
    }
}
