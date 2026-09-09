package com.zeus.springwildflyarch.sms;

import com.zeus.framework.sms.ZeusSmsClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * zeus-sms'in standart tipte (com.zeus.soap opt-in) çalıştığını gösteren örnek uç.
 *
 * <p>{@link ZeusSmsClient} bean'i YALNIZCA {@code zeus.sms.endpoint} tanımlıysa kurulur (bkz.
 * {@code ZeusSmsAutoConfiguration}) — bu bilinçli bir tasarımdır: SMS kullanmayan bir uygulama
 * ya da profil (ör. {@code local}) CXF'e hiç dokunmasın diye. Bu yüzden bean doğrudan kurucuya
 * enjekte EDİLMEZ: bu, endpoint tanımsızken tüm Spring context'inin açılmasını engeller ve
 * SMS'le hiç ilgisi olmayan uç noktaları da kırar. Bunun yerine {@link ObjectProvider} ile
 * tembel çözülür; bean yoksa istek 503 ile karşılanır, uygulamanın geri kalanı etkilenmez.
 */
@RestController
public class SmsDemoController {

    private final ObjectProvider<ZeusSmsClient> smsProvider;

    public SmsDemoController(ObjectProvider<ZeusSmsClient> smsProvider) {
        this.smsProvider = smsProvider;
    }

    @PostMapping("/api/sms")
    public ResponseEntity<String> gonder(@RequestParam String to, @RequestParam String text) {
        ZeusSmsClient sms = smsProvider.getIfAvailable();
        if (sms == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("zeus-sms yapılandırılmamış (zeus.sms.endpoint tanımsız).");
        }
        return ResponseEntity.ok(sms.send(to, text));
    }
}
