package com.minitwit.config;

import com.minitwit.model.LoginResult;
import com.minitwit.model.Message;
import com.minitwit.model.User;
import com.minitwit.service.impl.MiniTwitService;
import org.apache.commons.beanutils.BeanUtils;
import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.UrlEncoded;
import org.hydrogen.ClasspathDirectory;
import org.hydrogen.Handler;
import org.hydrogen.Request;
import org.hydrogen.Response;
import org.hydrogen.Router;
import org.hydrogen.Session;
import org.hydrogen.Status;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebConfig {
    private static final String USER_SESSION_ID = "user";
    private final FreemarkerRenderer renderer = new FreemarkerRenderer();
    private final MiniTwitService service;

    private WebConfig(MiniTwitService service) {
        this.service = service;
    }

    public static Handler of(MiniTwitService service) {
        WebConfig config = new WebConfig(service);
        return Router.builder()
                .bind("/", new ClasspathDirectory("public/"))
                .get("/", config.requireUser(config::index))
                .get("/public", config::publicTimeline)
                .get("/t/:username", config.userExists(config::userTimeline))
                .get("/t/:username/follow",
                        config.requireUser(config.userExists(config::followUser)))
                .get("/t/:username/unfollow",
                        config.requireUser(config.userExists(config::unfollowUser)))
                .get("/login", config.requireNoUser(config::loginForm))
                .post("/login", config::login)
                .get("/register", config.requireNoUser(config::registerForm))
                .post("/register", config::register)
                .post("/message", config.requireUser(config::message))
                .get("/logout", config::logout)
                .build();
    }

    private Session addAuthenticatedUser(Request request, User u) {
        return request.getSession().withAttribute(USER_SESSION_ID, u);
    }

    private Session removeAuthenticatedUser(Request request) {
        return request.getSession().withoutAttribute(USER_SESSION_ID);
    }

    private Response followUser(Request request) {
        String username = request.getRouteParam(":username");
        User profileUser = service.getUserbyUsername(username);
        User authUser = getAuthenticatedUser(request);
        service.followUser(authUser, profileUser);
        return Response.redirect("/t/" + username);
    }

    private User getAuthenticatedUser(Request request) {
        return request.getSession().getAttribute(USER_SESSION_ID);
    }

    private boolean hasAuthenticatedUser(Request request) {
        return request.getSession().hasAttribute(USER_SESSION_ID);
    }

    private Response loginForm(Request req) {
        Map<String, Object> map = new HashMap<>();
        if (req.hasQueryParam("r")) {
            map.put("message", "You were successfully registered and can login now");
        }
        return render("login.ftl", map);
    }

    private Response login(Request req) {
        Map<String, Object> map = new HashMap<>();
        User user = new User();
        try {
            MultiMap<String> params = new MultiMap<>();
            UrlEncoded.decodeTo(req.getBody(), params, "UTF-8", 1024 * 1024, 1024);
            BeanUtils.populate(user, params);
        } catch (Exception e) {
            return Response.status(Status.NOT_IMPLEMENTED).emptyBody();
        }
        LoginResult result = service.checkUser(user);
        if (result.getUser() != null) {
            return Response.found("/")
                    .session(addAuthenticatedUser(req, result.getUser()))
                    .emptyBody();
        } else {
            map.put("error", result.getError());
        }
        map.put("username", user.getUsername());
        return render("login.ftl", map);
    }

    private Handler requireNoUser(Handler handler) {
        return request -> {
            if (!hasAuthenticatedUser(request)) {
                return handler.handle(request);
            } else return Response.redirect("/");
        };
    }

    private Response registerForm(Request req) {
        return render("register.ftl", new HashMap<>());
    }

    private Response register(Request req) {
        Map<String, Object> map = new HashMap<>();
        User user = new User();
        try {
            MultiMap<String> params = new MultiMap<>();
            UrlEncoded.decodeTo(req.getBody(), params, "UTF-8", 1024 * 1024, 1024);
            BeanUtils.populate(user, params);
        } catch (Exception e) {
            return Response.status(Status.NOT_IMPLEMENTED).emptyBody();
        }
        String error = user.validate();
        if (StringUtils.isEmpty(error)) {
            User existingUser = service.getUserbyUsername(user.getUsername());
            if (existingUser == null) {
                service.registerUser(user);
                return Response.redirect("/login?r=1");
            } else {
                error = "The username is already taken";
            }
        }
        map.put("error", error);
        map.put("username", user.getUsername());
        map.put("email", user.getEmail());
        return render("register.ftl", map);
    }

    private Handler requireUser(Handler handler) {
        return request -> {
            if (hasAuthenticatedUser(request)) {
                return handler.handle(request);
            } else {
                return Response.redirect("/public");
            }
        };
    }

    private Response index(Request request) {
        User user = getAuthenticatedUser(request);
        Map<String, Object> map = new HashMap<>();
        map.put("pageTitle", "Timeline");
        map.put("user", user);
        List<Message> messages = service.getUserFullTimelineMessages(user);
        map.put("messages", messages);
        return render("timeline.ftl", map);
    }

    private Response publicTimeline(Request request) {
        User user = getAuthenticatedUser(request);
        Map<String, Object> map = new HashMap<>();
        map.put("pageTitle", "Public Timeline");
        map.put("user", user);
        List<Message> messages = service.getPublicTimelineMessages();
        map.put("messages", messages);
        return render("timeline.ftl", map);
    }

    private Response unfollowUser(Request req) {
        String username = req.getRouteParam(":username");
        User profileUser = service.getUserbyUsername(username);
        User authUser = getAuthenticatedUser(req);

        service.unfollowUser(authUser, profileUser);
        return Response.redirect("/t/" + username);
    }

    private Handler userExists(Handler handler) {
        return (request) -> {
            String username = request.getRouteParam(":username");
            User profileUser = service.getUserbyUsername(username);
            if (profileUser == null) {
                return Response.notFound().text("User Not Found");
            }

            return handler.handle(request);
        };
    }

    private Response userTimeline(Request request) {
        String username = request.getRouteParam(":username");
        User profileUser = service.getUserbyUsername(username);

        User authUser = getAuthenticatedUser(request);
        boolean followed = false;
        if (authUser != null) {
            followed = service.isUserFollower(authUser, profileUser);
        }
        List<Message> messages = service.getUserTimelineMessages(profileUser);

        Map<String, Object> map = new HashMap<>();
        map.put("pageTitle", username + "'s Timeline");
        map.put("user", authUser);
        map.put("profileUser", profileUser);
        map.put("followed", followed);
        map.put("messages", messages);
        return render("timeline.ftl", map);
    }

    private Response message(Request request) {
        try {
            User user = getAuthenticatedUser(request);
            MultiMap<String> params = new MultiMap<>();
            UrlEncoded.decodeTo(request.getBody(), params, "UTF-8", 1024 * 1024, 1024);
            Message m = new Message();
            m.setUserId(user.getId());
            m.setPubDate(new Date());
            BeanUtils.populate(m, params);
            service.addMessage(m);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Response.redirect("/");
    }

    private Response logout(Request request) {
        return Response.found("/public")
                .session(removeAuthenticatedUser(request))
                .emptyBody();
    }

    private Response render(String template, Map<String, Object> model) {
        return Response.ok().html(renderer.render(template, model));
    }
}
