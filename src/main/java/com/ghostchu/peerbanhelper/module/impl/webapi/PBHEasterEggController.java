package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.util.context.IgnoreScan;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@IgnoreScan
public class PBHEasterEggController  extends AbstractFeatureModule {
    private final JavalinWebContainer javalinWebContainer;
    private final List<String> urls = new ArrayList<>();

    public PBHEasterEggController(JavalinWebContainer javalinWebContainer) {
        super();
        this.javalinWebContainer = javalinWebContainer;
        urls.add("https://www.bilibili.com/video/BV1S3UaYvE5o");
        urls.add("https://www.bilibili.com/video/BV1QQ4y1d7Rd");
        urls.add("https://www.bilibili.com/video/BV1by4y1F7nS");
        urls.add("https://www.bilibili.com/video/BV1ztzBY1EPy");
        urls.add("https://www.bilibili.com/video/BV15Z4y1Q7mz");
        urls.add("https://www.bilibili.com/video/BV1GJ411x7h7");
        urls.add("https://www.bilibili.com/video/BV1a14y187U5");
        urls.add("https://www.bilibili.com/video/BV1na411Q7qH");
        urls.add("https://music.163.com/song?id=2647873613&uct2=U2FsdGVkX1+Uch9IV1F1N/gLpHilPuUZQXy0xDGsKrM=");
        urls.add("https://music.163.com/song?id=2165896793&uct2=U2FsdGVkX19w55MRVVy7ZG3Yf9d6QL+CIwgE8jPc4N0=");
        urls.add("https://www.youtube.com/watch?v=tSLI6PjAfns");
        urls.add("https://www.bilibili.com/video/BV1Nb41117L3");
        urls.add("https://www.bilibili.com/video/BV1js411A71E");
        urls.add("https://store.steampowered.com/app/365450/Hacknet/");
        urls.add("https://store.steampowered.com/app/753640/Outer_Wilds/");
        urls.add("https://store.steampowered.com/app/739630/Phasmophobia/");
        urls.add("https://store.steampowered.com/app/3590/Plants_vs_Zombies_GOTY_Edition/");
        urls.add("https://store.steampowered.com/app/333600/NEKOPARA_Vol_1/");
        urls.add("https://store.steampowered.com/app/391540/Undertale/");
        urls.add("https://store.steampowered.com/app/646570/Slay_the_Spire/");
        urls.add("https://store.steampowered.com/app/457140/_/");
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - Access from :9898/api/egg";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-easteregg";
    }

    /**
     * Enables the web routes for the PBHEasterEggController module.
     *
     * Sets up three GET endpoints on the Javalin web server:
     * <ul>
     *   <li>{@code /api/egg}: Redirects to a random URL from the predefined list</li>
     *   <li>{@code /api/neuro}: Serves a specific image resource related to Neuro-sama</li>
     *   <li>{@code /api/neurosama}: Serves the same image resource as {@code /api/neuro}</li>
     * </ul>
     *
     * @see #handleEgg(Context)
     * @see #neuro(Context)
     */
    @Override
    public void onEnable() {
        javalinWebContainer.javalin()
                .get("/api/egg", this::handleEgg)
                .get("/api/neuro", this::neuro) // AI VTuber made by Vedal on Twitch: https://www.twitch.tv/vedal987
                .get("/api/neurosama", this::neuro);
    }


    /**
     * Handles the `/api/neuro` endpoint by serving a specific PNG image resource.
     *
     * This method attempts to retrieve the "neuro.png" image from the classpath. If the image
     * is not found, it responds with a 404 status and an error message. When the image is
     * successfully located, it returns the image with a 420 (Enhance Your Calm) status code.
     *
     * @param context The Javalin request context used to handle the HTTP request and response
     * @throws IOException if there are issues reading the image resource stream
     */
    private void neuro(Context context) {
        // Yeeeeet, Neuro!
        var imageStream = Main.class.getResourceAsStream("/assets/other/neuro.png");
        if (imageStream == null) {
            context.status(HttpStatus.NOT_FOUND);
            context.result("You killed Neuro! How dare you!?");
            return;
        }
        context
                .status(HttpStatus.ENHANCE_YOUR_CALM)
                .contentType(ContentType.IMAGE_PNG)
                .result(imageStream);
    }

    /**
     * Redirects the client to a randomly selected URL from the predefined list of URLs.
     *
     * This method uses {@link ThreadLocalRandom} to select a random index from the {@code urls} list
     * and performs an HTTP redirect to the corresponding URL. It provides a simple mechanism for
     * generating random Easter egg redirections.
     *
     * @param context The Javalin request context used to perform the HTTP redirect
     */
    private void handleEgg(Context context) {
        int index = ThreadLocalRandom.current().nextInt(urls.size());
        context.redirect(urls.get(index));
    }

    @Override
    public void onDisable() {

    }
}
