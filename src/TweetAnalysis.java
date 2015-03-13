import java.util.*;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import twitter4j.*;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;
import java.sql.*;

import com.mongodb.*;



public class TweetAnalysis
{
	
	private static boolean ignoreSmallWords = false;
	private static boolean ignoreSingleChars = true;
	private static boolean ignoreMentions = true;
	private static String [] smallWords = {"_","a","al","also","an","and","are",
		"as","at","be","because","been","both","by","can","due","e","et",
		"fig","first","for","from","has","have","here","i","if","in",
		"into","is","it","not","of","on","one","only","or","s","second",
		"see","so","such","than","that","the","their","then","there",
		"these","they","third","this","three","thus","to","two","used",
		"using","very","was","we","well","when","where","whereas","which","with"};
	
	private static int timeBinSize = 60;
	private static Twitter twitter = null;
	private static TwitterStream twitterStream;
	
	private static DBCollection coll;
	
	private static long[] userIDs;
	
	static Pattern pattern;
	
	public static void newStatus(Status status)
	{
		long statusID = status.getUser().getId();
		BasicDBObject queryObj = new BasicDBObject("users.twitter id", statusID);
		BasicDBObject returnFields = new BasicDBObject("users.twitter id", 1).append("users.$", 1);
		System.out.println(coll.findOne(queryObj, returnFields));
		BasicDBObject dbUser = (BasicDBObject)((BasicDBList) coll.findOne(queryObj, returnFields).get("users")).get(0);
		System.out.println(dbUser.keySet());
		
		
		FrequencyList timeFrequencyList = new FrequencyList((BasicDBList) dbUser.get("time frequencies"), "time");
		FrequencyList wordFrequencyList = new FrequencyList((BasicDBList) dbUser.get("word frequencies"), "word");
		FrequencyList appFrequencyList = new FrequencyList((BasicDBList) dbUser.get("app frequencies"), "app");
		
		int linkCount = (Integer) dbUser.get("tweets with links");
		int tweetCount = (Integer) dbUser.get("tweets") + 1;
		if(status.getURLEntities().length > 0) {
			linkCount++;
    		dbUser.put("tweets with links",  linkCount);
		}
		
		dbUser.append("$set", new BasicDBObject().append("tweets", 110));
		dbUser.put("link ratio", (double) linkCount/tweetCount);
    	((BasicDBList) dbUser.get("statuses")).add(getStatusDBObject(status));
    	
    	// update word count
    	String text = status.getText();
    	String [] words = pattern.split(text);
    	for(String word : words)
    	{
    		if( (!ignoreSingleChars || word.length()>1) &&
    			(!ignoreSmallWords || Arrays.binarySearch(smallWords, word)<0) &&
    			(!ignoreMentions || word.charAt(0) != '@')) 
    		{
    			wordFrequencyList.add(word.toLowerCase());
    		}
    	}
    	
    	// update app frequency count
    	Pattern appPattern = Pattern.compile("<.*?>");
    	String app = appPattern.split(status.getSource())[1];
    	appFrequencyList.add(app);
    	
    	// update time count
    	Date date = status.getCreatedAt();
    	int time = (int) (date.getTime()/60000 - 240)%(24*60);
    	int binNum = time/60;
    	String timeString = (binNum + ":00-" + binNum + ":59");
    	timeFrequencyList.add(timeString);
    	
	    dbUser.put("word frequencies", wordFrequencyList.sort());
	    dbUser.put("app frequencies", appFrequencyList.sort());
	    dbUser.put("time frequencies", timeFrequencyList.sort());
	    
	    ((BasicDBList) coll.findOne(queryObj, returnFields).get("users")).put(0, dbUser);
	    coll.update(queryObj, dbUser, true, false);
	}
	
	public static void runAnalysis() throws TwitterException
	{
		ConfigurationBuilder cb = new ConfigurationBuilder();
		cb.setDebugEnabled(true)
		  .setOAuthConsumerKey("cL9ApUu4Qy5W6RxCGwDybRdCu")
		  .setOAuthConsumerSecret("aV09LkOdgDaCXSpDb7opxhYkdlpdDOBCCBec67BFM1aUnb5ZpR")
		  .setOAuthAccessToken("54558885-LHd4FoXBYvEsPxvipgdej9gMaQQO7ZZd7Z09bZFtZ")
		  .setOAuthAccessTokenSecret("yU6nxmzYjwRiYLfx9ZAdoaHVVUY19KPudJzYFa12zGbPo");
		Configuration conf = cb.build();
		TwitterFactory tf = new TwitterFactory(conf);
		twitter = tf.getInstance();
		

		twitterStream = new TwitterStreamFactory(conf).getInstance();
		FilterQuery query = new FilterQuery();
		
		UserStreamAdapter listener = new UserStreamAdapter(){

			public void onStatus(Status status) {
				if(!status.isRetweet() && status.getUserMentionEntities().length==0)
					newStatus(status);
			}

			public void onException(Exception arg0) {
				arg0.printStackTrace();
			}
		};
		String [] users = {"@LilTunechi", "@VanessaHudgens", "813286", "22412376", "268414482", "@PutinRF"};
		
		userIDs = new long[users.length];
		for(int i=0; i<users.length; i++) {
		    if(users[i].charAt(0) == '@') {
	    		userIDs[i] = twitter.showUser(users[i].substring(1)).getId();
		    }
	    	else {
	    		userIDs[i] = Long.parseLong(users[i]);
	    	}
		}
		Arrays.sort(userIDs);
		
		BasicDBList userList = new BasicDBList();
		for(int i=0; i<users.length; i++) {
			userList.add(analyzeUser(userIDs[i]));
		}
		
		coll.insert(new BasicDBObject("users",userList));
		query.follow(userIDs);
		twitterStream.addListener(listener);
		twitterStream.filter(query);
		System.out.println("listening...");
	}
	
	public static BasicDBObject analyzeUser(long userID) throws TwitterException
	{
	    User user = twitter.showUser(userID);
		
	    List<twitter4j.Status> statuses = null;
	    BasicDBList dbStatusList = new BasicDBList();
	    FrequencyList wordFrequencyList = new FrequencyList("word");
	    FrequencyList appFrequencyList = new FrequencyList("app");
	    FrequencyList timeFrequencyList = new FrequencyList("time");
	    
	    int linkCount = 0, statusCount = 0;
	    
	    Pattern pattern = Pattern.compile("([^a-zA-Z0-9@]+)?(^| |\n)([^a-zA-Z0-9@]+)?");
	    
	    for(int i=1; statuses==null || !statuses.isEmpty(); i++)
	    {
	    	statuses = twitter.getUserTimeline(userID, new Paging(i, 200));
	    	
		    for (twitter4j.Status status : statuses)
		    {
		    	if(status.getURLEntities().length > 0)
		    		linkCount++;
		    	statusCount++;
		    	
		    	dbStatusList.add(getStatusDBObject(status));
		    	
		    	// update word count
		    	String text = status.getText();
		    	String [] words = pattern.split(text);
		    	for(String word : words)
		    	{
		    		
		    		if( (!ignoreSingleChars || word.length()>1) &&
		    			(!ignoreSmallWords || Arrays.binarySearch(smallWords, word)<0) &&
		    			(!ignoreMentions || word.charAt(0) != '@')) 
		    		{
		    			wordFrequencyList.add(word.toLowerCase());
		    		}
		    	}
		    	
		    	// update app frequency count
		    	Pattern appPattern = Pattern.compile("<.*?>");
		    	String app = appPattern.split(status.getSource())[1];
		    	appFrequencyList.add(app);
		    	
		    	// update time count
		    	Date date = status.getCreatedAt();
		    	int time = (int) (date.getTime()/60000 - 240)%(24*60);
		    	int binNum = time/timeBinSize;
		    	String timeString = (binNum + ":00-" + binNum + ":59");
		    	timeFrequencyList.add(timeString);
		    }
	    }
	    
	    wordFrequencyList.sort();
	    appFrequencyList.sort();
	    timeFrequencyList.sort();
	    
    	BasicDBObject userObj = new BasicDBObject("twitter id", userID);
    	userObj.append("name", user.getName());
    	userObj.append("statuses", dbStatusList);
    	userObj.append("word frequencies", wordFrequencyList);
    	userObj.append("app frequencies", appFrequencyList);
    	userObj.append("time frequencies", timeFrequencyList);
    	userObj.append("friends", user.getFriendsCount());
    	userObj.append("followers", user.getFollowersCount());
    	userObj.append("tweets", statusCount);
    	userObj.append("tweets with links", linkCount);
    	userObj.append("link ratio", (double) linkCount/statusCount);

    	System.out.println(userID + " done");
    	
    	return userObj;
    	
	}
	
	public static void startDatabase() throws ClassNotFoundException, SQLException {
		
		MongoClient mongoClient = new MongoClient();
		
		@SuppressWarnings("deprecation")
		DB db = mongoClient.getDB("mydb");
		
		coll = db.getCollection("twitterDatabase");
	}
	
	public static BasicDBObject getStatusDBObject(Status s) {
		
		BasicDBObject obj = new BasicDBObject("date", s.getCreatedAt().toString());
    	obj.append("favorite count", s.getFavoriteCount());
    	obj.append("retweet count", s.getRetweetCount());
    	obj.append("source", s.getSource());
    	obj.append("text", s.getText());
    	obj.append("twitter_id", s.getId());
		return obj;
	}
	
	public static void main(String [] args) throws TwitterException, ClassNotFoundException, SQLException
	{
		pattern = Pattern.compile("([^a-zA-Z0-9@]+)?(^| |\n)([^a-zA-Z0-9@]+)?");
		startDatabase();
		runAnalysis();
	}
}
