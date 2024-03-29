package deathpool;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.PropertySource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.common.collect.Lists;

import fastily.jwiki.core.Wiki;

@Component
@PropertySource("classpath:application.properties")
public class PoolRunner implements ApplicationRunner {

	private static final int BASE_AGE = 135;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Value("${spreadsheet.id}")
	private String spreadsheetId;

	private static final Pattern PATTERN = Pattern.compile("^|.*death_date.*\\{(.*)\\}$");

	@Autowired
	private Sheets sheets;

	@Autowired
	private Wiki wiki;

	@Autowired
	private JavaMailSender sender;


	private void processPooler(Pooler pooler) throws GeneralSecurityException, IOException {
		final String range = String.format("%s%d:%s%d", pooler.getNameColumn(), pooler.getStartRow(),
				pooler.getResultColumn(), pooler.getEndRow());
		ValueRange response = sheets.spreadsheets().values().get(spreadsheetId, range).execute();
		List<List<Object>> values = response.getValues();
		for (List<Object> row : values) {
			String name = (String) row.get(0);
			Integer score = Integer.parseInt((String) row.get(1));
			logger.info("Celeb name: {}", name);
			if(score != null && score.intValue() != 0) {
				logger.info("Using existing non-zero score {} for {}", score, name);
				pooler.getResults().put(name, score);
			} else {
				Integer status = checkCelebrity(name);
				pooler.getResults().put(name, status);
			}
			pooler.getNames().add(name);
		}
	}

	private void updateSpreadSheet(Pooler pooler) throws GeneralSecurityException, IOException {
		final String range = String.format("%s%d:%s%d", pooler.getResultColumn(), pooler.getStartRow(),
				pooler.getResultColumn(), pooler.getEndRow());

		List<String> names = pooler.getNames();
		List<List<Object>> values = new ArrayList<>();
		for (String name : names) {
			List<Object> newArrayList = Lists.newArrayList(pooler.getResults().get(name));
			values.add(newArrayList);
		}
		ValueRange body = new ValueRange().setValues(values);
		UpdateValuesResponse result = sheets.spreadsheets().values().update(spreadsheetId, range, body)
				.setValueInputOption("RAW").execute();
		logger.info("{} cells updated.", result.getUpdatedCells());

	}

	private Integer checkCelebrity(String name) {

		String pageText = wiki.getPageText(name);
		if (StringUtils.isEmpty(pageText)) {
			return 0;
		}
		String[] lines = pageText.split("\n");
		boolean dead = false;
		String deathInfo = null;
		Integer score = 0;
		for (String string : lines) {
			if (string.equals("")) {
				continue;
			}
			Matcher matcher = PATTERN.matcher(string);
			if (matcher.matches()) {
				deathInfo = matcher.group(1);
				deathInfo = deathInfo.replaceAll("}", "");
				logger.info("deathIno: {}", deathInfo);
				String[] fields = deathInfo.split("\\|");
				Calendar deathCal = Calendar.getInstance();
				Calendar birthCal = Calendar.getInstance();
				deathCal.set(Integer.parseInt(fields[1]), Integer.parseInt(fields[2]), Integer.parseInt(fields[3]));
				birthCal.set(Integer.parseInt(fields[4]), Integer.parseInt(fields[5]), Integer.parseInt(fields[6]));
				Period period = new Period(new DateTime(birthCal.getTime()), new DateTime(deathCal.getTime()));
				int age = period.getYears();
				logger.info("age = {}", age);
				score = BASE_AGE - age;
				logger.info("score = {}", score);
				dead = true;
				break;
			}
		}
		logger.info("dead = {}", dead);
		logger.info("deathInfo = {}", deathInfo);
		if (dead) {
			return score;
		}
		return 0;
	}

	private void sendmail(Pooler... poolers) throws AddressException, MessagingException, IOException {

		StringBuffer buffer = new StringBuffer();
		for (Pooler pooler : poolers) {
			Integer sum = pooler.getResults().values().stream().collect(Collectors.summingInt(Integer::intValue));
			buffer.append(pooler.getName() + ": " + sum);
			buffer.append("\n");
		}
		buffer.append("\n");
		buffer.append("https://docs.google.com/spreadsheets/d/1o9MWe4gshiO4aYslkWczZ0TOKuUaoB5fr8Gt6osdN_M/edit#gid=0");
		for (Pooler pooler : poolers) {
			MimeMessage message = sender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message);
			helper.setTo(pooler.getEmail());
			helper.setText(buffer.toString());
			helper.setSubject("DeathPool Results");
			sender.send(message);
		}
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		Pooler melissa = new Pooler("Melissa", "mel.dailey@gmail.com", "A", "B", 2, 48);
		Pooler birch = new Pooler("Birch", "birchsport@gmail.com", "C", "D", 2, 42);
		Pooler nuno = new Pooler("Nuno", "nmgoncal@yahoo.com", "E", "F", 2, 45);
		processPooler(melissa);
		updateSpreadSheet(melissa);
		processPooler(birch);
		updateSpreadSheet(birch);
		processPooler(nuno);
		updateSpreadSheet(nuno);

		logger.info("Melissa: {}", melissa);
		logger.info("Birch: {}", birch);
		logger.info("Nuno: {}", nuno);
		sendmail(melissa, birch, nuno);
	}
}