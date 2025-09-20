package com.repairo.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Custom Error Controller for handling various HTTP error scenarios
 * Provides user-friendly error pages with consistent design
 */
@Controller
public class CustomErrorController implements ErrorController {

    private static final String ERROR_PATH = "/error";

    @RequestMapping(ERROR_PATH)
    public String handleError(HttpServletRequest request, Model model) {
        // Get error status
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        
        // Get error details
        String errorMessage = (String) request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        String requestUri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        Exception exception = (Exception) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        
        // Add common attributes to model
        model.addAttribute("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        model.addAttribute("path", requestUri);
        
        if (status != null) {
            int statusCode = Integer.parseInt(status.toString());
            model.addAttribute("status", String.valueOf(statusCode));
            
            // Add error message based on status code
            switch (statusCode) {
                case 404:
                    model.addAttribute("error", "Page Not Found");
                    model.addAttribute("message", errorMessage != null ? errorMessage : "The requested page could not be found");
                    return "error/404";
                    
                case 403:
                    model.addAttribute("error", "Access Forbidden");
                    model.addAttribute("message", errorMessage != null ? errorMessage : "You don't have permission to access this resource");
                    return "error/403";
                    
                case 500:
                    model.addAttribute("error", "Internal Server Error");
                    model.addAttribute("message", errorMessage != null ? errorMessage : "An unexpected error occurred on the server");
                    return "error/500";
                    
                case 400:
                    model.addAttribute("error", "Bad Request");
                    model.addAttribute("message", errorMessage != null ? errorMessage : "The request was invalid or malformed");
                    break;
                    
                case 401:
                    model.addAttribute("error", "Unauthorized");
                    model.addAttribute("message", errorMessage != null ? errorMessage : "Authentication is required to access this resource");
                    break;
                    
                case 405:
                    model.addAttribute("error", "Method Not Allowed");
                    model.addAttribute("message", errorMessage != null ? errorMessage : "The HTTP method is not supported for this resource");
                    break;
                    
                case 408:
                    model.addAttribute("error", "Request Timeout");
                    model.addAttribute("message", errorMessage != null ? errorMessage : "The server timed out waiting for the request");
                    break;
                    
                case 502:
                    model.addAttribute("error", "Bad Gateway");
                    model.addAttribute("message", errorMessage != null ? errorMessage : "The server received an invalid response from upstream");
                    break;
                    
                case 503:
                    model.addAttribute("error", "Service Unavailable");
                    model.addAttribute("message", errorMessage != null ? errorMessage : "The service is temporarily unavailable");
                    break;
                    
                case 504:
                    model.addAttribute("error", "Gateway Timeout");
                    model.addAttribute("message", errorMessage != null ? errorMessage : "The server timed out waiting for upstream response");
                    break;
                    
                default:
                    model.addAttribute("error", "Error " + statusCode);
                    model.addAttribute("message", errorMessage != null ? errorMessage : "An unexpected error occurred");
                    break;
            }
        } else {
            // Default error handling
            model.addAttribute("status", "500");
            model.addAttribute("error", "Internal Server Error");
            model.addAttribute("message", errorMessage != null ? errorMessage : "An unexpected error occurred");
        }
        
        // Add exception details if available (for debugging)
        if (exception != null) {
            model.addAttribute("exception", exception.getClass().getSimpleName());
            // Only add stack trace in development mode
            // You can add a check for active profile here
            // if (Arrays.asList(environment.getActiveProfiles()).contains("dev")) {
            //     model.addAttribute("trace", ExceptionUtils.getStackTrace(exception));
            // }
        }
        
        // Return generic error template
        return "error";
    }

    /**
     * Custom mapping for handling 404 errors specifically
     */
    @RequestMapping("/404")
    public String handle404(Model model) {
        model.addAttribute("status", "404");
        model.addAttribute("error", "Page Not Found");
        model.addAttribute("message", "The page you're looking for doesn't exist");
        model.addAttribute("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return "error/404";
    }

    /**
     * Custom mapping for handling 500 errors specifically
     */
    @RequestMapping("/500")
    public String handle500(Model model) {
        model.addAttribute("status", "500");
        model.addAttribute("error", "Internal Server Error");
        model.addAttribute("message", "Something went wrong on our end");
        model.addAttribute("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return "error/500";
    }

    /**
     * Custom mapping for handling 403 errors specifically
     */
    @RequestMapping("/403")
    public String handle403(Model model) {
        model.addAttribute("status", "403");
        model.addAttribute("error", "Access Forbidden");
        model.addAttribute("message", "You don't have permission to access this resource");
        model.addAttribute("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return "error/403";
    }
}