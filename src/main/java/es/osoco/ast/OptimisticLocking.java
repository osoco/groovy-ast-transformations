/*
 * Copyright 2003-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package es.osoco.ast;

import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker interface to mark a Grails' controller action to deal with the exception fired by the optimistic locking 
 * mechanism used by the GORM classes. An AST transformation will later wire this together. 
 * 
 * This transformation synthetizes code consisting in a try-catch statement wrapping the original action's block of code.
 * The catch statement handles the {@code org.springframework.dao.OptimisticLockingFailureException} fired when the
 * current versioned state of a persistent instance differs from its version property value. The catch block will store 
 * the resulting of resolving the given message code (or its default value if a message code is not speficied) as the 
 * 'message' property in the flash scope, and will redirect to the action specified, passing as parameters a submap of 
 * the parameters received by the annotated action.
 *
 * For a given controller action like the following:
 *
 * <pre>
 * @OptimisticLocking(
 *     redirect='dealLocking'
 *     params='id',
 *     messageCode='optimistic.failure.code
 * def update = {
 *     def domain = Domain.get(params.id)            
 *     if ((domain.version > params['domain.version']?.toLong()) {
 *         throw new OptimisticLockingFailureException("optimistic.locking")
 *     }
 *     group.properties = params
 *     redirect action: 'show', id: domain.id
 * }
 * </pre>
 *
 * the code resulting from the transformation will be the following:
 *
 * <pre>
 * def update = {
 *     try {
 *         def domain = Domain.get(params.id)            
 *         if ((domain.version > params['domain.version']?.toLong()) {
 *             throw new OptimisticLockingFailureException("optimistic.locking")
 *         }
 *         group.properties = params
 *         redirect action: 'show', id: domain.id
 *     } catch (org.springframework.dao.OptimisticLockingFailureException olfe) {
 *         flash.message = message('optimistic.failure.code')
 *         redirect action: 'dealLocking', params: params.subMap(['id'])
 *     }
 * }
 * </pre>
 *
 * @author Rafael Luque (OSOCO)
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.FIELD})
@GroovyASTTransformationClass("es.osoco.transform.OptimisticLockingASTTransformation")
public @interface OptimisticLocking {

    final static String DEFAULT_REDIRECT_ACTION = "edit";

    final static String DEFAULT_REDIRECT_PARAMS = "id";

    final static String DEFAULT_FLASH_MESSAGE_CODE = "optimistic.locking.failure";

    /**
     * @return the action name to redirect in the case of stale data detected.
     */
    String redirect() default DEFAULT_REDIRECT_ACTION;

    /**
     * @return the comma-separated list of param names to be used in the redirection.
     */
    String params() default DEFAULT_REDIRECT_PARAMS;

    /**
     * @return the error message key to use in the case of stale data.
     */
    String messageCode() default DEFAULT_FLASH_MESSAGE_CODE;

}
