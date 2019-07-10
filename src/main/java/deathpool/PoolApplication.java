package deathpool;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;

import fastily.jwiki.core.Wiki;

@SpringBootApplication
@PropertySource("classpath:application.properties")
public class PoolApplication {

    public static void main(String[] args) {
        SpringApplication.run(PoolApplication.class, args);
    }

    @Value("${creds.file:credentials.json}")
	private String credsFile;

    @Value("${creds.dir:creds-dir}")
	private String credsDir;

    @Value("${app.name}")
	private String appName;

    @Bean
    public Credential credential() {
    	GoogleClientSecrets clientSecrets;
		try {
			clientSecrets = GoogleClientSecrets.load(JacksonFactory.getDefaultInstance(),
					new FileReader(credsFile));
		List<String> scopes = Arrays.asList(SheetsScopes.SPREADSHEETS);
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
				GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance(), clientSecrets,
				scopes).setDataStoreFactory(new FileDataStoreFactory(new File(credsDir))).build();
		Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
		return credential;
		} catch (IOException | GeneralSecurityException e) {
			e.printStackTrace();
		}
		return null;
    }

    @Bean
    public Wiki wiki() {
		return new Wiki("en.wikipedia.org");
    }

    @Bean
    public Sheets sheets() throws IOException, GeneralSecurityException {
		return new Sheets.Builder(GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance(),
				credential()).setApplicationName(appName).build();
	}
}
