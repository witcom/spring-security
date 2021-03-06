/*
 * Copyright 2002-2013 the original author or authors.
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
package org.springframework.security.config.annotation.web.configuration

import org.springframework.mock.web.MockServletContext
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.core.userdetails.PasswordEncodedUser
import org.springframework.security.core.userdetails.User
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.BaseSpringSpec
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter
import org.springframework.security.web.debug.DebugFilter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class EnableWebSecurityTests extends BaseSpringSpec {

	def "@Bean(BeanIds.AUTHENTICATION_MANAGER) includes HttpSecurity's AuthenticationManagerBuilder"() {
		when:
			loadConfig(SecurityConfig)
			AuthenticationManager authenticationManager = context.getBean(AuthenticationManager)
			AnonymousAuthenticationToken anonymousAuthToken = findFilter(AnonymousAuthenticationFilter).createAuthentication(new MockHttpServletRequest())
		then:
			authenticationManager.authenticate(new UsernamePasswordAuthenticationToken("user", "password"))
			authenticationManager.authenticate(anonymousAuthToken)

	}


	@EnableWebSecurity
	static class SecurityConfig extends WebSecurityConfigurerAdapter {
		@Override
		protected void configure(AuthenticationManagerBuilder auth) throws Exception {
			auth
				.inMemoryAuthentication()
					.withUser(PasswordEncodedUser.user());
		}

		@Bean
		@Override
		public AuthenticationManager authenticationManagerBean()
				throws Exception {
			return super.authenticationManagerBean();
		}

		@Override
		protected void configure(HttpSecurity http) throws Exception {
			http
				.authorizeRequests()
					.antMatchers("/*").hasRole("USER")
					.and()
				.formLogin();
		}
	}

	def "@EnableWebSecurity on superclass"() {
		when:
			loadConfig(ChildSecurityConfig)
		then:
			context.getBean("springSecurityFilterChain", DebugFilter)
	}

	@Configuration
	static class ChildSecurityConfig extends DebugSecurityConfig {
	}

	@EnableWebSecurity(debug=true)
	static class DebugSecurityConfig extends WebSecurityConfigurerAdapter {

	}

	def "SEC-2942: EnableWebSecurity adds AuthenticationPrincipalArgumentResolver"() {
		setup:
		def username = "test"
		context = new AnnotationConfigWebApplicationContext()
		context.servletContext = new MockServletContext()
		context.register(AuthenticationPrincipalConfig)
		context.refresh()
		SecurityContext securityContext = new SecurityContextImpl(authentication: new TestingAuthenticationToken(username, "pass", "ROLE_USER"))
		MockMvc mockMvc = MockMvcBuilders
				.webAppContextSetup(context)
				.addFilters(springSecurityFilterChain)
				.build()
		when:
		String body = mockMvc
				.perform(get("/").sessionAttr(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext))
				.andReturn().response.contentAsString
		then:
		body == username

	}

	@EnableWebSecurity
	@EnableWebMvc
	@Configuration
	static class AuthenticationPrincipalConfig {
		@Autowired
		public void configureGlobal(AuthenticationManagerBuilder auth) {
			auth.inMemoryAuthentication()
		}

		@RestController
		static class AuthController {

			@RequestMapping("/")
			String principal(@AuthenticationPrincipal String principal) {
				principal
			}
		}
	}
}
