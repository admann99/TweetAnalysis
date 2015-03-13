import java.util.Collections;
import java.util.Comparator;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;


public class FrequencyList extends BasicDBList
{
	private static final long serialVersionUID = 1157560415613448606L;
	private String label;
	
	private class Pair extends BasicDBObject implements Comparator<Object>{
		private static final long serialVersionUID = -5523630204794312991L;

		private Pair(String key) {
			this.put(label, key);
			this.put("frequency", 1);
		}
		
		private Pair(BasicDBObject obj) {
			this.put(label, obj.getString(label));
			this.put("frequency", obj.getInt("frequency"));
		}
		
		private Pair(Object obj) {
			this((BasicDBObject) obj);
		}
		
		private void increment() {
			this.put("frequency", this.getInt("frequency")+1);
		}
		private String getString() {
			return this.getString(label);
		}

		public int compare(Object p1, Object p2) {
			return new Pair(p2).getInt("frequency") - new Pair(p1).getInt("frequency");
		}
		
	}
	
	public FrequencyList(BasicDBList list, String label) {
		this(label);
		this.putAll(list);
	}
	
	public FrequencyList(String label) {
		this.label = label;
	}
	
	public void add(String str) {
		Pair pair = findString(str);
		if(pair == null)
			super.add(new Pair(str));
		else
			pair.increment();
	}
	
	public FrequencyList sort() {
		this.sort(new Pair(""));
		return this;
	}
	
	private Pair findString(String str) {
		int size = this.size();
		for(int i=0; i<size; i++) {
			Pair p = new Pair(this.get(i));
			if(p.getString().equals(str))
				return p;
		}
		return null;
	}
	
	public String getLabel() {
		return label;
	}

}
