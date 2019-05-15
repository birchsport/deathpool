package deathpool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class Pooler {

	private final String nameColumn;
	private final String resultColumn;
	private final int startRow;
	private final int endRow;
	private Map<String, Integer> results = new HashMap<>();
	private List<String> names = new ArrayList<>();

}