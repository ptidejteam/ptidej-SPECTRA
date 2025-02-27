package ca.concordia.encs.citydata.core;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/* This java class is to print all available routes */

@RestController
@RequestMapping("/routes")
public class RouteController {

	private final RequestMappingHandlerMapping handlerMapping;

	@Autowired
	public RouteController(ApplicationContext applicationContext) {
		this.handlerMapping = applicationContext.getBean(RequestMappingHandlerMapping.class);
	}

	@GetMapping("/list")
	public List<String> listAllRoutes() {
		List<String> routes = new ArrayList<>();

		handlerMapping.getHandlerMethods().forEach((requestMappingInfo, handlerMethod) -> {
			String methodInfo = handlerMethod.getMethod().toString();

			List<String> paths = requestMappingInfo.getDirectPaths() != null
					? requestMappingInfo.getDirectPaths().stream().toList()
					: requestMappingInfo.getPatternsCondition() != null
							? requestMappingInfo.getPatternsCondition().getPatterns().stream().toList()
							: List.of("[No Route Patterns]");

			String methods = requestMappingInfo.getMethodsCondition() != null
					? requestMappingInfo.getMethodsCondition().toString()
					: "[No Methods Condition]";

			paths.forEach(path -> {
				// Exclude Spring Boot's default error routes
				if (!path.equals("/error") && !path.startsWith("/error")) {
					routes.add("Method: " + methods + " | Path: " + path + " | Handler: " + methodInfo);
				}
			});
		});

		routes.sort(null);
		return routes;
	}
}