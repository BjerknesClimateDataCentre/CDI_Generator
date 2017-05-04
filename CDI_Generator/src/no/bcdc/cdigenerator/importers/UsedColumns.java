package no.bcdc.cdigenerator.importers;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class UsedColumns implements Iterable<Column> {

	List<Column> columns;
	
	public UsedColumns() {
		columns = new ArrayList<Column>();
	}
	
	public void add(String columnName, int columnIndex, boolean numeric) {
		columns.add(new Column(columnName, columnIndex, numeric));
	}
	
	public Column get(int index) {
		return columns.get(index);
	}
	
	public Iterator<Column> iterator() {
		return columns.iterator();
	}
	
	public int size() {
		return columns.size();
	}
}
