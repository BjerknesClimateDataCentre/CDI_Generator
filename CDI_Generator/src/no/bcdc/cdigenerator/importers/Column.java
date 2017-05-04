package no.bcdc.cdigenerator.importers;

public class Column {

	private String name;
	
	private int index;
	
	private boolean numeric;
		
	public Column(String name, int index, boolean numeric) {
		this.name = name;
		this.index = index;
		this.numeric = numeric;
	}

	public String getName() {
		return name;
	}
	
	public int getIndex() {
		return index;
	}
	
	public boolean isNumeric() {
		return numeric;
	}
}
