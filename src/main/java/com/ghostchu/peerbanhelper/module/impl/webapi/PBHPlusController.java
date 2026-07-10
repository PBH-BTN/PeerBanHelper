package com.ghostchu.peerbanhelper.module.impl.webapi;

import com.ghostchu.peerbanhelper.Main;
import com.ghostchu.peerbanhelper.module.AbstractFeatureModule;
import com.ghostchu.peerbanhelper.module.impl.webapi.body.LicenseDeleteBody;
import com.ghostchu.peerbanhelper.module.impl.webapi.body.LicensePutRequestBody;
import com.ghostchu.peerbanhelper.module.impl.webapi.body.PowCaptchaBody;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.LicenseKeyPairDTO;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.PBHPlusStatusDTO;
import com.ghostchu.peerbanhelper.module.impl.webapi.dto.PowCaptchaData;
import com.ghostchu.peerbanhelper.pbhplus.LicenseDownloader;
import com.ghostchu.peerbanhelper.pbhplus.LicenseManager;
import com.ghostchu.peerbanhelper.pbhplus.LocalKeyManager;
import com.ghostchu.peerbanhelper.pbhplus.bean.V1License;
import com.ghostchu.peerbanhelper.pbhplus.bean.V2License;
import com.ghostchu.peerbanhelper.text.Lang;
import com.ghostchu.peerbanhelper.util.pow.PoWServer;
import com.ghostchu.peerbanhelper.web.JavalinWebContainer;
import com.ghostchu.peerbanhelper.web.Role;
import com.ghostchu.peerbanhelper.web.wrapper.StdResp;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.javalin.http.Context;
import io.javalin.openapi.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.ghostchu.peerbanhelper.text.TextManager.tlUI;

@Component
public final class PBHPlusController extends AbstractFeatureModule {
    @Autowired
    private JavalinWebContainer webContainer;
    @Autowired
    private LicenseManager licenseManager;
    @Autowired
    private LicenseDownloader licenseDownloader;
    @Autowired
    private LocalKeyManager localKeyManager;
    private final Cache<String, PoWServer> powCaptcha = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();
    private final int powCaptchaDifficultyBits = 5;
    private final String powCaptchaAlgorithm = "SHA-1";



    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public @NotNull String getName() {
        return "WebAPI - PBH Plus Interface";
    }

    @Override
    public @NotNull String getConfigName() {
        return "webapi-pbh-plus";
    }

    @Override
    public void onEnable() {
        webContainer.routes()
                .get("/api/pbhplus/status", this::handleStatus, Role.USER_READ)
                .put("/api/pbhplus/key", this::handleLicensePut, Role.USER_WRITE)
                .delete("/api/pbhplus/key", this::handleLicenseDelete, Role.USER_WRITE)
                .get("/api/pbhplus/claimRenewFreeLicenseCaptcha", this::claimRenewFreeLicenseCaptcha, Role.USER_WRITE)
                .post("/api/pbhplus/renewFreeLicense", this::handleLicenseRenew, Role.USER_WRITE);
    }

    @OpenApi(
            path = "/api/pbhplus/claimRenewFreeLicenseCaptcha",
            methods = HttpMethod.GET,
            summary = "获取免费许可证验证码",
            description = "获取续订免费许可证所需的工作量证明验证码",
            tags = {"PBH+"},
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "claimRenewFreeLicenseCaptcha"
    )
    private void claimRenewFreeLicenseCaptcha(@NotNull Context context) {
        var server = new PoWServer(powCaptchaDifficultyBits, powCaptchaAlgorithm);
        var challengeId = UUID.randomUUID().toString();
        powCaptcha.put(challengeId, server);
        context.json(new StdResp(true, null, new PowCaptchaData(challengeId, Base64.getEncoder().encodeToString(server.getChallenge()), server.getDifficultyBits(), powCaptchaAlgorithm)));
    }

    @OpenApi(
            path = "/api/pbhplus/key",
            methods = HttpMethod.PUT,
            summary = "添加许可证",
            description = "添加并激活 PBH+ 许可证",
            tags = {"PBH+"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LicensePutRequestBody.class)),
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "handleLicensePut"
    )
    private void handleLicensePut(@NotNull Context context) throws Exception {
        var body = context.bodyAsClass(LicensePutRequestBody.class);
        var input = body.key().trim();
        processLicensePut(context, input, false);
    }

    private void processLicensePut(Context context, String input, boolean selfCall) throws Exception {
        try {
            var license = licenseManager.getLicenseParser().fromLicense(input);
            var status = licenseManager.getLicenseBackend().getLicenseStatus(license);
            switch (status) {
                case EXPIRED -> context.json(new StdResp(false, tlUI(Lang.PBH_LICENSE_KEY_EXPIRED), null));
                case REVOKED -> context.json(new StdResp(false, tlUI(Lang.PBH_LICENSE_KEY_REVOKED), null));
                case VALID, NOT_STARTED -> {
                    var keyList = Main.getMainConfig().getStringList("pbh-plus-key");
                    if (!keyList.contains(input))
                        keyList.add(input);
                    Main.getMainConfig().set("pbh-plus-key", keyList);
                    Main.getMainConfig().save(Main.getMainConfigFile());
                    licenseManager.reloadModule();
                    context.json(new StdResp(true, tlUI(Lang.PBH_PLUS_LICENSE_UPDATED), null));
                }
                default -> context.json(new StdResp(false, tlUI(Lang.PBH_LICENSE_PARSE_FAILED), null));
            }
        } catch (NoSuchPaddingException | IllegalBlockSizeException | NoSuchAlgorithmException |
                 InvalidKeySpecException | BadPaddingException | IOException | InvalidKeyException e) {
            if (!selfCall) {
                var downloadedLicense = licenseDownloader.attemptDownloadFromServer(input);
                if (downloadedLicense != null) {
                    processLicensePut(context, downloadedLicense, true);
                    return;
                }
            }
            context.json(new StdResp(false, tlUI(Lang.PBH_LICENSE_PARSE_FAILED), null));
        }
    }

    @OpenApi(
            path = "/api/pbhplus/renewFreeLicense",
            methods = HttpMethod.POST,
            summary = "续订免费许可证",
            description = "提交验证码并续订免费 PBH+ 许可证",
            tags = {"PBH+"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = PowCaptchaBody.class)),
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "handleLicenseRenew"
    )
    private void handleLicenseRenew(@NotNull Context context) throws Exception {
        var body = context.bodyAsClass(PowCaptchaBody.class);
        var captchaServer = powCaptcha.getIfPresent(body.getCaptchaId());
        if (captchaServer == null) {
            context.json(new StdResp(false, tlUI(Lang.POW_CAPTCHA_SERVER_NOT_FOUND), null));
            return;
        }
        if (!captchaServer.verify(Base64.getDecoder().decode(body.getCaptchaNonce()))) {
            context.json(new StdResp(false, tlUI(Lang.POW_CAPTCHA_SERVER_VERIFY_FAILED), null));
            return;
        }
        if (licenseManager.isFeatureEnabled("basic")) {
            context.json(new StdResp(false, tlUI(Lang.PBH_PLUS_FREE_LICENSE_DENIED_EXISTS), null));
            return;
        }
        var local = localKeyManager.generateLocalLicense();
        var keyList = Main.getMainConfig().getStringList("pbh-plus-key");
        if (!keyList.contains(local)) {
            keyList.add(local);
            Main.getMainConfig().set("pbh-plus-key", keyList);
            Main.getMainConfig().save(Main.getMainConfigFile());
        }
        licenseManager.reloadModule();
        context.json(new StdResp(true, tlUI(Lang.PBH_PLUS_LICENSE_UPDATED), null));
    }

    @OpenApi(
            path = "/api/pbhplus/key",
            methods = HttpMethod.DELETE,
            summary = "删除许可证",
            description = "删除指定的 PBH+ 许可证",
            tags = {"PBH+"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LicenseDeleteBody.class)),
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "handleLicenseDelete"
    )
    private void handleLicenseDelete(@NotNull Context context) throws Exception {
        String key = context.bodyAsClass(LicenseDeleteBody.class).getLicenseId();
        var mappedLicense = licenseManager.getLicenseBackend().getLicensesMap().get(key);
        if (licenseManager == null) {
            context.json(new StdResp(false, tlUI(Lang.PBH_LICENSE_KEY_DELETE_FAILED_NOT_EXISTS), "Not found mappedLicense"));
            return;
        }
        var keyList = Main.getMainConfig().getStringList("pbh-plus-key");
        if (keyList.remove(mappedLicense.getKeyText())) {
            Main.getMainConfig().set("pbh-plus-key", keyList);
            Main.getMainConfig().save(Main.getMainConfigFile());
            licenseManager.reloadModule();
            context.json(new StdResp(true, tlUI(Lang.PBH_LICENSE_KEY_DELETED), null));
        } else {
            context.json(new StdResp(false, tlUI(Lang.PBH_LICENSE_KEY_DELETE_FAILED_NOT_EXISTS), "Not found target keyText"));
        }
    }

    @OpenApi(
            path = "/api/pbhplus/status",
            methods = HttpMethod.GET,
            summary = "获取 PBH+ 状态",
            description = "获取当前 PBH+ 功能和许可证状态",
            tags = {"PBH+"},
            responses = {
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StdResp.class)),
                    @OpenApiResponse(status = "403", content = @OpenApiContent(from = StdResp.class))
            },
            operationId = "pbhPlusStatus"
    )
    private void handleStatus(@NotNull Context context) {
        List<LicenseKeyPairDTO> licenseKeyPairDTOList = new ArrayList<>();
        licenseManager.getLicenseBackend().getLicensesMap().forEach((licenseId, license) -> {
            var dto = new LicenseKeyPairDTO(licenseId,
                    license instanceof V2License ? 2 : (license instanceof V1License ? 1 : -1),
                    licenseManager.getLicenseBackend().getLicenseStatus(license),
                    license);
            licenseKeyPairDTOList.add(dto);
        });
        context.json(new StdResp(true, null, new PBHPlusStatusDTO(licenseManager.getLicenseBackend().getAllFeatures(), licenseKeyPairDTOList)));
    }

    @Override
    public void onDisable() {

    }
}
