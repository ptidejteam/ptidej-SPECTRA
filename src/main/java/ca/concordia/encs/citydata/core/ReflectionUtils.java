package ca.concordia.encs.citydata.core;

import java.lang.reflect.Method;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
/***
 * This class contains all helping methods for reflection
 * @Author: Rushin Makwana
 * @Date: 7th Feb 2024
 */
public class ReflectionUtils {

	public static JsonElement getRequiredField(JsonObject jsonObject, String fieldName) {
		if (!jsonObject.has(fieldName)) {
			throw new IllegalArgumentException("Error: Missing '" + fieldName + "' field");
		}
		return jsonObject.get(fieldName);
	}

	public static Object instantiateClass(String className) throws Exception {
		Class<?> clazz = Class.forName(className);
		return clazz.getDeclaredConstructor().newInstance();
	}

	public static void setParameters(Object instance, JsonArray params) throws Exception {
		Class<?> clazz = instance.getClass();
		for (JsonElement paramElement : params) {
			JsonObject paramObject = paramElement.getAsJsonObject();
			String paramName = paramObject.get("name").getAsString();
			JsonElement paramValue = paramObject.get("value");
			Method setter = findSetterMethod(clazz, paramName, paramValue);
			setter.invoke(instance, convertValue(setter.getParameterTypes()[0], paramValue));
		}
	}

	public static Method findSetterMethod(Class<?> clazz, String paramName, JsonElement paramValue)
			throws NoSuchMethodException {
		String methodName = "set" + capitalize(paramName);
		for (Method method : clazz.getMethods()) {
			if (method.getName().equals(methodName) && method.getParameterCount() == 1) {
				return method;
			}
		}
		throw new NoSuchMethodException("No suitable setter found for " + paramName);
	}

	public static Object convertValue(Class<?> targetType, JsonElement value) {
		if (targetType == int.class || targetType == Integer.class) {
			return value.getAsInt();
		} else if (targetType == boolean.class || targetType == Boolean.class) {
			return value.getAsBoolean();
		} else if (targetType == double.class || targetType == Double.class) {
			return value.getAsDouble();
		} else if (targetType == JsonObject.class) {
			return value.getAsJsonObject();
		} else if (targetType == JsonArray.class) {
			return value.getAsJsonArray();
		}
		return value.getAsString();
	}

	public static String capitalize(String str) {
		return str == null || str.isEmpty() ? str : str.substring(0, 1).toUpperCase() + str.substring(1);
	}
}