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
package es.osoco.ast

import org.springframework.dao.OptimisticLockingFailureException

/**
 * Unit tests for the {@code @OptimisticLocking} local AST transformation.
 * 
 * @author Rafael Luque (OSOCO)
 */
class OptimisticLockingTransformTests extends GroovyTestCase
{

    def shell
    def binding

    /**
     * Setup a GroovyShell instance with the current classloader.
     */    
    protected void setUp() {
        super.setUp()
        binding = new Binding()
        shell =  new GroovyShell(this.class.classLoader, binding)
    }
    

    protected void tearDown() {        
        super.tearDown()
        shell = null
        binding = null
    }


    /**
     * Tests the OptimisticLocking annotation behaviour when an 
     * {@code OptimisticLockingFailureException} is thrown by the
     * annotated closure.
     */
    void testOptimisticLockingHandlingLockingFailure() {   
        def res = shell.evaluate("""
            import org.springframework.dao.OptimisticLockingFailureException
            import es.osoco.ast.OptimisticLocking

            class X {
                def params = [:]
                def flash = [:]
                def redirectArgs

                @OptimisticLocking
                def action = {
                    throw new OptimisticLockingFailureException("failure")
                }

                private redirect(args) {
                    redirectArgs = args
                }

                private message(args) {
                    args.code
                }
            }

            def x = new X()
            x.params = [id: 1, other: 2]
            x.action()
            x   
        """)
        
        assertEquals OptimisticLocking.DEFAULT_FLASH_MESSAGE_CODE, res.flash.message
        assertEquals OptimisticLocking.DEFAULT_REDIRECT_ACTION, res.redirectArgs.action
        assertEquals res.params.subMap([OptimisticLocking.DEFAULT_REDIRECT_PARAMS]), res.redirectArgs.params
    }


    /**
     * Tests the OptimisticLocking annotation using a custom action for redirection.
     */    
    void testCustomRedirectOptimisticLocking() {   
        String customRedirect = "otherAction"
        
        binding.setVariable("customRedirect", customRedirect)
        def res = shell.evaluate("""
            import org.springframework.dao.OptimisticLockingFailureException
            import es.osoco.ast.OptimisticLocking

            class X {
                def params = [:]
                def flash = [:]
                def redirectArgs

                @OptimisticLocking(redirect="${customRedirect}")
                def action = {
                    throw new OptimisticLockingFailureException("failure")
                }

                private redirect(args) {
                    redirectArgs = args
                }

                private message(args) {
                    args.code
                }
            }

            def x = new X()
            x.params = [id: 1, other: 2]
            x.action()
            x   
        """)
        
        assertEquals OptimisticLocking.DEFAULT_FLASH_MESSAGE_CODE, res.flash.message
        assertEquals customRedirect, res.redirectArgs.action
        assertEquals res.params.subMap([OptimisticLocking.DEFAULT_REDIRECT_PARAMS]), res.redirectArgs.params
    }


    /**
     * Tests the OptimisticLocking annotation using a custom list of parameters for redirection.
     */    
    void testCustomRedirectParamsOptimisticLocking() {   
        def params = [id: 1, other: 2, param1: 3, param2: 4]
        String customRedirectParams = "param1, param2, notExistingParam"
        
        binding.setVariable("customRedirectParams", customRedirectParams)
        binding.setVariable("params", params)
        def res = shell.evaluate("""
            import org.springframework.dao.OptimisticLockingFailureException
            import es.osoco.ast.OptimisticLocking

            class X {
                def params = [:]
                def flash = [:]
                def redirectArgs

                @OptimisticLocking(params="${customRedirectParams}")
                def action = {
                    throw new OptimisticLockingFailureException("failure")
                }

                private redirect(args) {
                    redirectArgs = args
                }

                private message(args) {
                    args.code
                }
            }

            def x = new X()
            x.params = ${params}
            x.action()
            x   
        """)
        
        assertEquals OptimisticLocking.DEFAULT_FLASH_MESSAGE_CODE, res.flash.message
        assertEquals OptimisticLocking.DEFAULT_REDIRECT_ACTION, res.redirectArgs.action
        def expectedParams = params.subMap(['param1', 'param2'])
        expectedParams.put("notExistingParam", null)
        assertEquals expectedParams, res.redirectArgs.params
    }


    /**
     * Tests the OptimisticLocking annotation using a custom message error code.
     */    
    void testCustomMessageCodeOptimisticLocking() {   
        String customMessageCode = "otherMessage"
        
        binding.setVariable("customMessageCode", customMessageCode)
        def res = shell.evaluate("""
            import org.springframework.dao.OptimisticLockingFailureException
            import es.osoco.ast.OptimisticLocking

            class X {
                def params = [:]
                def flash = [:]
                def redirectArgs

                @OptimisticLocking(messageCode="${customMessageCode}")
                def action = {
                    throw new OptimisticLockingFailureException("failure")
                }

                private redirect(args) {
                    redirectArgs = args
                }

                private message(args) {
                    args.code
                }
            }

            def x = new X()
            x.params = [id: 1, other: 2]
            x.action()
            x   
        """)
        
        assertEquals customMessageCode, res.flash.message
        assertEquals OptimisticLocking.DEFAULT_REDIRECT_ACTION, res.redirectArgs.action
        assertEquals res.params.subMap([OptimisticLocking.DEFAULT_REDIRECT_PARAMS]), res.redirectArgs.params
    }


    /**
     * Tests whether OptimisticLocking annotation does not interfere the
     * annotated closure behaviour when the {@code OptimisticLockingFailureException} 
     * is NOT thrown.
     */
    void testOptimisticLockingWithoutLockingFailure() {   
        String flashMessage = "Success"
        
        binding.setVariable("flashMessage", flashMessage)
        def res = shell.evaluate("""
            import org.springframework.dao.OptimisticLockingFailureException
            import es.osoco.ast.OptimisticLocking

            class X {
                def params = [:]
                def flash = [:]
                def redirectArgs

                @OptimisticLocking
                def action = {
                    flash.message = "${flashMessage}"
                }

                private redirect(args) {
                    redirectArgs = args
                }

                private message(args) {
                    args.code
                }
            }

            def x = new X()
            x.params = [id: 1]
            x.action()
            x   
        """)
        
        assertEquals flashMessage, res.flash.message
    }

}
