/*
 * 
 * SimpleQueryExecutor.java
 * Assume we use colstore as the store method for now
 * later, we use a parent store  method
 * execute queries
 * if input data format is "each line is a JSON object " 
 * test example: ./run SimpleQueryExecutor ColStoreEng testjson/abc10.json testjson/test.sql   
 * if input data format is "JSON array " 
 * test example: ./run SimpleQueryExecutor ColStoreEng testjson/testarray.json testjson/testnb.sql 1  
 * ./run SimpleQueryExecutor ColStoreEng testjson/testarray.json testjson/nobench.query 1
 *
 * @author Jin Chen, Alan Lu
 */

import java.io.FileReader;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.File;
import javax.json.Json;
import javax.json.JsonReader;
import javax.json.JsonValue;
import javax.json.JsonObject;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonString;
import java.nio.ByteBuffer;
import java.util.Calendar;
import java.util.Hashtable;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
public class SimpleQueryExecutor {

	public class Query {
    	int type; //0:empty 1:select only 2: select where
    	String [] parameters;
    	Query(){
    	    type = 0;
    	}
	}

    public static StoreEngine store;
	public static String defFile;
	public static String layoutFile;
    private Hashtable <String, String> tableDef = new Hashtable<String, String>();
    
    // querySet is for optimizing the batch query -- do it later 
    //int maxNumQuery = 1000; //max number of queries -- could be more 
    //Query [] querySet;
	public SimpleQueryExecutor (String storeMethod, String datafile, int datafile_format) { 
        int buffer_size = 100*1000*1000; 
        if(storeMethod.equals("ColStoreEng")){
            buffer_size = 2*1000*1000; //smaller for col store
            store = new ColStoreEng(buffer_size);  
        }else if(storeMethod.equals("RowStoreEng")){
			buffer_size = 2*1024*1024*1024-4;
			if(defFile == null){
				//a defintion file is missing, scan the datafile and print one
				defFile = datafile+".def";
				makeDefFile(datafile, defFile, datafile_format);
			}
			store = new RowStoreEng(buffer_size, defFile);
		}else if(storeMethod.equals("RowColStoreEng")){
			buffer_size = (2*1024*1024*1024-4)/3;
			//buffer_size = 2*1000*1000;
			store = new RowColStoreEng(buffer_size, layoutFile);
		}else{
            System.out.println("Wrong Store Engine name! Use ColStoreEng or RowStoreEng or RowColStoreEng "); 
            System.exit(-1);
        }
        //querySet = new Query[1000];
    }

	public void scanRow(JsonValue tree, String key){
        switch(tree.getValueType()){
            case OBJECT:
                JsonObject object = (JsonObject) tree;
                for(String name: object.keySet()){
                    if(key!=null)
                        scanRow(object.get(name),key+"."+name);
                    else
                        scanRow(object.get(name),name);
                }
                break;
            case ARRAY:
                JsonArray array = (JsonArray) tree;
                int index =0;
                for (JsonValue val : array){
                    scanRow(val,key+"["+index+"]");
                    index += 1;
                }
                break;
            case STRING:
                tableDef.put(key+":STRING", "");
                break;
            case NUMBER:
                JsonNumber num = (JsonNumber) tree;
                if(num.isIntegral()){
                    tableDef.put(key+":LONG", "");
                }else{
                    tableDef.put(key+":DOUBLE", "");
                }
                break;
            case TRUE:
            case FALSE:
                tableDef.put(key+":BOOL", "");
                break;
            case NULL:
                break;
        }
	}

	//For rowStore, scan the datafile, then output a file showing all fields and their types
    public void makeDefFile(String datafile, String outputfile, int format)
    {
        try{
            if(format == 1){
                //input data is a JSON array format 
                FileInputStream is = new FileInputStream(datafile);
                JsonReader reader = Json.createReader(is);
                JsonArray array = reader.readArray();
                for (int i = 0; i< array.size() ;i++){
                    JsonObject ob = array.getJsonObject(i);
                    scanRow(ob,null);
                }
            }else if(format==0){
                // the data is multiple JSON objects 
                BufferedReader bufferedReader = new BufferedReader(new FileReader(datafile));
                String line = bufferedReader.readLine();
                int objid = 0;
                while (line !=null){
                    objid  = objid + 1;
                    InputStream is = new ByteArrayInputStream(line.getBytes());
                    JsonReader reader = Json.createReader(is);
                    JsonObject jsonob = reader.readObject();
                    scanRow(jsonob,null);
                    line = bufferedReader.readLine();
                }
            }
			//after scanning all object, print the tabledef file:
			File f = new File(outputfile);
			if(f.exists() && !f.isDirectory()) { 
				f.delete();
			}
			BufferedWriter bw = new BufferedWriter(new FileWriter(f, true));
			for(String key: tableDef.keySet()){
				//String type = tableDef.get(key);
				bw.write(key+" ");
			}
			bw.close();
        } catch (FileNotFoundException e){
            System.err.println("FileNotFoundException:"+e.getMessage());
            return ;
        } catch (IOException e){
            System.err.println("IOException:"+e.getMessage());
            return ;
        }
    }

    /* input the data set into the store */ 
    /* Later: move this to insert query */
    public void init(String datafile, int format)
    {
        try{
            if(format == 1){
                //input data is a JSON array format 
                FileInputStream is = new FileInputStream(datafile);
                JsonReader reader = Json.createReader(is);
                JsonArray array = reader.readArray();
                for (int i = 0; i< array.size() ;i++){
                    JsonObject ob = array.getJsonObject(i);
                    store.insertObject(i,ob,null);
                }
                //System.out.println("array size"+array.size());

            }else if(format==0){
                // the data is multiple JSON objects 
                BufferedReader bufferedReader = new BufferedReader(new FileReader(datafile));
                String line = bufferedReader.readLine();
                int objid = 0;
                while (line !=null){
                    //System.out.println(line);
                    objid  = objid + 1;
                    InputStream is = new ByteArrayInputStream(line.getBytes());
                    JsonReader reader = Json.createReader(is);
                    JsonObject jsonob = reader.readObject();
                    store.insertObject(objid,jsonob,null);
                    line = bufferedReader.readLine();
                }
            }
        } catch (FileNotFoundException e){
            System.err.println("FileNotFoundException:"+e.getMessage());
            return ;
        } catch (IOException e){
            System.err.println("IOException:"+e.getMessage());
            return ;
        }
        
        //if(storeMethod.equals("RowColStoreEng")){
            //store.printTable("xxx.txt");
        //}
    }

    /* parse simplified SQL query: one table no nested query */
    /* later: parse real sql query using existing library*/ 
    public Query parse(String queryString)
    {
        Query query = new Query();
        query.type = 0; // init to be empty query
        
        //queryString = queryString.toLowerCase(); - delete - avoid lower string value 
        if(queryString.endsWith(";")){
            queryString = queryString.substring(0,queryString.length()-1);
        }
        if(queryString.startsWith("SELECT") == true){
            if(queryString.contains("COUNT(*)" ) == true){
                // aggregation 
                if(queryString.contains("WHERE")==false){
                    query.type = 11; //select only aggregation
                }else{
                    //check where clause
                    if(queryString.contains("GROUP BY") == true){
                        if(queryString.contains("BETWEEN") &&  queryString.contains("AND")){
                            query.type = 13; //select where range and group by 
                            String [] tokens1 = queryString.split("SELECT")[1].trim().split("GROUP BY");
                            String whereString = tokens1[0].trim(); 
                            query.parameters = new String [5];
                            query.parameters[4] = tokens1[1].trim(); //group by column
                            String [] tokens=whereString.split("WHERE",2);
                            query.parameters[0] = tokens[0].trim() ; //select columns
                            String [] whereTokens = tokens[1].split("BETWEEN",2);
                            query.parameters[1] = whereTokens[0].trim(); // where columns
                            String [] rangeTokens = whereTokens[1].split("AND",2);
                            query.parameters[2] = rangeTokens[0].trim(); // small value
                            query.parameters[3] = rangeTokens[1].trim(); // large value
                    //System.out.println("select "+query.parameters[0]+" where "+query.parameters[1]+" range "+query.parameters[2]+ " "+query.parameters[3]+" group by "+query.parameters[4]);
                        }

                    }
                }

            } // end if aggregation query
            else 
            if(queryString.contains("WHERE") == false){
                query.type = 1; //select only
                String [] tokens = queryString.split("SELECT",2);
                query.parameters = new String[1];
                query.parameters[0] = tokens[1].trim(); 
                //System.out.println("select "+query.parameters[0]);
            }else{
                // check equal where or range where 
                // we need to write a better parser
                // we can only handle one single where column for now
                // select where ANY -- put it before select where
               if(queryString.contains("=") && queryString.contains("ANY")){
                    query.type = 4; //select where value = ANY xx
                    String whereString = queryString.split("SELECT")[1].trim();
                    String [] tokens=whereString.split("WHERE",2);
                    query.parameters = new String [4];
                    query.parameters[0] = tokens[0].trim() ; //select columns
                    String [] valueTokens = tokens[1].split("=",2);
                    query.parameters[2] = valueTokens[0].trim().replaceAll("^\"|\"$",""); //trim " from  where value
                    String [] whereTokens = valueTokens[1].trim().split("ANY",2); 
                    query.parameters[1] = whereTokens[1].trim(); // where columns
                    //System.out.println("SELECT "+query.parameters[0]+" WHERE "+query.parameters[2]+" = ANY "+query.parameters[1]);
                }
                else if(queryString.contains("=") == true){
                    query.type = 2; //select where equal
                    String whereString = queryString.split("SELECT")[1].trim();
                    String [] tokens=whereString.split("WHERE",2);
                    query.parameters = new String [3];
                    query.parameters[0] = tokens[0].trim() ; //select columns
                    String [] whereTokens = tokens[1].split("=",2);
                    query.parameters[1] = whereTokens[0].trim(); // where columns
                    // remove begining and end quotes from the value  
                    query.parameters[2] = whereTokens[1].trim().replaceAll("^\"|\"$",""); // trim " from where value
                    
                    //System.out.println("SELECT "+query.parameters[0]+" WHERE "+query.parameters[1]+" = "+query.parameters[2]);
                }
                else if(queryString.contains("BETWEEN") &&  queryString.contains("AND")){ 
                    query.type = 3; // select where range query
                    String whereString = queryString.split("SELECT")[1].trim();
                    String [] tokens=whereString.split("WHERE",2);
                    query.parameters = new String [4];
                    query.parameters[0] = tokens[0].trim() ; //select columns
                    String [] whereTokens = tokens[1].split("BETWEEN",2);
                    query.parameters[1] = whereTokens[0].trim(); // where columns
                    String [] rangeTokens = whereTokens[1].split("AND",2);
                    query.parameters[2] = rangeTokens[0].trim(); // small value
                    query.parameters[3] = rangeTokens[1].trim(); // large value
                    //System.out.println("SELECT "+query.parameters[0]+" WHERE "+query.parameters[1]+" range "+query.parameters[2]+ " "+query.parameters[3]);
                }
                else{
                    System.out.println("Unknown select where query "+queryString);
                }
            }//end of if
        }else if(queryString.startsWith("aggregate")==true){
            query.type = 10; //aggregate
            String [] tokens = queryString.split("aggregate");
            query.parameters = new String[1];
            query.parameters[0] = tokens[1].trim(); 
            System.out.println("aggregate "+query.parameters[0]);
        }else{
            System.out.println("Unknown query "+queryString);
        }   
        return query;
    }

    public void print_results(HashMap<Integer, HashMap<String, String>> resultSet)
    {
    /*
        System.out.println("Query Results: " + resultSet.size() + " objects selected");
        for(Integer objid: resultSet.keySet()){
            System.out.print(Integer.toString(objid) + " ");
            HashMap <String, String> innerSet = resultSet.get(objid);
                for(String key: innerSet.keySet()){
                    String value = innerSet.get(key);
                    System.out.print(key + ":" + value + " ");
                }
                System.out.println();
        }
     
        int fieldCount = 0;
        for(Integer objid: resultSet.keySet()){
            HashMap <String, String> innerSet = resultSet.get(objid);
            fieldCount += innerSet.size();
        }
        
        System.out.println("Query Results: " + resultSet.size() + " objects selected in total " + fieldCount + " fields");
*/
    }
    public void print_agg_results(Hashtable <String,Integer> resultSet)
    {
        //System.out.println("Query Aggregation Results Size: " + resultSet.size()  );
        /*
        for(String key: resultSet.keySet()){
            
            System.out.println(key+", "+resultSet.get(key));
        }
        */

    }
    public String execute(Query query)
    {
        String results = "";

        //added by Alan to test SELECT all sparse fields
        if(query.parameters[0].equals("sparse_*")){
            StringBuffer strBuf = new StringBuffer();
            //String[] newPara = new String[1000];
            for(int i = 0; i < 1000; i++){
                strBuf.append( "sparse_" + String.format("%03d", i) );
                if(i != 999)
                    strBuf.append(",");
            }
            query.parameters[0] = strBuf.toString();
        }
        
        //System.out.println("execute ");
        switch (query.type){
            case 1: //select only clause
                String[] selectColumns = query.parameters[0].split(",");
                byte[][] columns = new byte[selectColumns.length][];
                for(int i=0; i<selectColumns.length; i++){
                    String column = selectColumns[i].trim();
                    //System.out.println("select only query: "+column);
                    columns[i] = column.getBytes();
                }
				//long start = System.currentTimeMillis();
				HashMap<Integer, HashMap<String, String>> resultSet = store.select(columns);
				//long end = System.currentTimeMillis();
				//System.out.print((end - start)+" ");
                print_results(resultSet);
                break;
                
            case 2: //select where  A = "value"
                selectColumns = query.parameters[0].split(",");
                byte[][] sColumns = new byte[selectColumns.length][];
                for(int i=0; i<selectColumns.length; i++){
                    String column = selectColumns[i].trim();
                    //System.out.println("select where  = query: "+column);
                    sColumns[i] = column.getBytes();
                }
                // single where column
                byte [] wColumn = query.parameters[1].getBytes();
                byte [] value = query.parameters[2].getBytes();
				//start = System.currentTimeMillis();
                resultSet = store.selectWhereSingle(sColumns, wColumn,"=", value);
				//end = System.currentTimeMillis();
				//System.out.print((end - start)+" ");
            	print_results(resultSet);
                break;
                
            case 3: //select where range
                selectColumns = query.parameters[0].split(",");
                sColumns = new byte[selectColumns.length][];
                for(int i=0; i<selectColumns.length; i++){
                    String column = selectColumns[i].trim();
                    //System.out.println("select where range query: "+column);
                    sColumns[i] = column.getBytes();
                }
                // single where column
                wColumn = query.parameters[1].getBytes();
                long small_value = Long.parseLong(query.parameters[2]);
                long large_value = Long.parseLong(query.parameters[3]);
				//long start = System.currentTimeMillis();
                resultSet = store.selectRange(sColumns, wColumn, small_value,large_value);
                //long end = System.currentTimeMillis();
				//System.out.print((end - start)+" ");
                print_results(resultSet);
                break;
                
            case 4: //select where any
                selectColumns = query.parameters[0].split(",");
                sColumns = new byte[selectColumns.length][];
                for(int i=0; i<selectColumns.length; i++){
                    String column = selectColumns[i].trim();
                    //System.out.println("select where any query: "+column);
                    sColumns[i] = column.getBytes();
                }
                // single where column
                wColumn = query.parameters[1].getBytes();
                value = query.parameters[2].getBytes();
				//start = System.currentTimeMillis();
                resultSet = store.selectWhereAny(sColumns, wColumn,"=", value);
				//end = System.currentTimeMillis();
				//System.out.print((end - start)+" ");
                print_results(resultSet);
                break;
            case 10://aggregate
                //only one column
                String colName = query.parameters[0];
                int threshold = 999999999; //very large number -- selectivity is 1
                results=String.valueOf(store.aggregate(colName.getBytes(),1000000)); 
                //System.out.println("Agg results:"+results);
                break;
            case 13: //aggregation where range , group by
                selectColumns = query.parameters[0].split(",");
                sColumns = new byte[selectColumns.length][];
                for(int i=0; i<selectColumns.length; i++){
                    String column = selectColumns[i].trim();
                    //System.out.println("select where range query: "+column);
                    sColumns[i] = column.getBytes();
                }
                // single where column
                wColumn = query.parameters[1].getBytes();
                small_value = Long.parseLong(query.parameters[2]);
                large_value = Long.parseLong(query.parameters[3]);
                byte[] gColumn = query.parameters[4].getBytes();
                Hashtable <String,Integer> aggResultSet = store.aggregateRangeGroupBy(sColumns, wColumn, small_value,large_value,gColumn);
                print_agg_results(aggResultSet);
                break;

            default:
                System.out.println("Executor: unknown query type!");
        }//end of switch
        return results;
    }

    /* execute a set of queries in batch
     * one line is one query 
     * execute query one by one -- optimizationlater   
     *
    public void batchRun(String queryfile)
    {
        try{
            BufferedReader bufferedReader = new BufferedReader(new FileReader(queryfile));
            String line = bufferedReader.readLine();
            int queryId = 0;
            Query query;
            while (line !=null){
                if(line.startsWith("#")==false){
                    //System.out.println(line);
                    query=parse(line);
                    if(query.type > 0){
                        long start = System.currentTimeMillis();
                        execute(query);
                        long end = System.currentTimeMillis();
                        Integer runtime= runTimes.get(line);
                        if (runtime==null){
                            runtime = 0;
                        }
                        runtime += (int) (end - start);
                        runTimes.put(line, runtime);
                        
                        Integer frequency = queryFreq.get(line);
                        if (frequency==null){
                            frequency = 0;
                        }
                        frequency++;
                        queryFreq.put(line, frequency);
//                        System.out.print((end - start)+" ");
                    }
                }
                line = bufferedReader.readLine();
            }
        } catch (FileNotFoundException e){
            System.err.println("FileNotFoundException:"+e.getMessage());
            return ;
        } catch (IOException e){
            System.err.println("IOException:"+e.getMessage());
            return ;
        }
        
    }*/

    /* execute a set of queries in batch
     * one line is one query
     * execute query one by one -- optimizationlater
     */
    public Hashtable<String, ArrayList<Long>> batchRun(String queryfile)
    {
        Hashtable<String, ArrayList<Long>> runTimes = new Hashtable<String, ArrayList<Long>>();
        try{
            BufferedReader bufferedReader = new BufferedReader(new FileReader(queryfile));
            String line = bufferedReader.readLine();
            int queryId = 0;
            Query query;
            while (line !=null){
                if(line.startsWith("#")==false){
                    query=parse(line);
                    if(query.type > 0){
                        long start = System.currentTimeMillis();
                        execute(query);
                        long end = System.currentTimeMillis();
                        Long time = new Long(end - start);
                        if(runTimes.get(line) == null){
                            runTimes.put(line, new ArrayList<Long>());
                        }
                        runTimes.get(line).add(time);
                    }
                }
                line = bufferedReader.readLine();
            }
        } catch (FileNotFoundException e){
            System.err.println("FileNotFoundException:"+e.getMessage());
        } catch (IOException e){
            System.err.println("IOException:"+e.getMessage());
        }
        return runTimes;
    }
    
    static long getMean(ArrayList<Long> data){
        long sum = 0;
        for(int i=0; i<data.size(); i++){
            sum += data.get(i).longValue();
        }
        return sum/data.size();
    }
    static long getVariance(ArrayList<Long> data){
        long mean = getMean(data);
        long temp = 0;
        for(int i=0; i<data.size(); i++){
            temp += (mean-data.get(i).longValue())*(mean-data.get(i).longValue());
        }
        return temp/data.size();
    }
    
    static double getStdDev(ArrayList<Long> data){
        return Math.sqrt((double)getVariance(data));
    }
    
    public static void main(String[] args) throws IOException{
        //System.out.println(args.length);
        if(args.length < 4) {
            System.out.println("Arguments: <store engine name, e.g. ColStoreEng>  <data file name> <query file name> <data file format: objects (0) or array (1)> <additional file name: for RowStore, this is the definition file; for RowColStore, this is the layout file>"); 
            System.exit(0);
        }
        String storeMethod = args[0];
        String datafile = args[1];
        String queryfile = args[2];
        int datafile_format = 0; // each line is a JSON object
        //if(args.length == 4)
        datafile_format = Integer.valueOf(args[3]); //the whole file is a JSON array
            
		if(storeMethod.equals("RowStoreEng")){
			if(args.length < 5){	
				System.out.println("Table definition file is missing, one will be generated"); 			
			}
			else{
				defFile = args[4];
			}
		}
		if(storeMethod.equals("RowColStoreEng")){
			if(args[4] == null){
				System.out.println("To use RowColStoreEng, a layout file is required"); 
            	System.exit(-1);
			}
			else{
				layoutFile = args[4];
			}
		}

		SimpleQueryExecutor engine = new SimpleQueryExecutor(storeMethod, datafile, datafile_format);
        // init to populate the data
        engine.init(datafile,datafile_format);
        
        // read input query file and execute the query one by one
		//long start = System.currentTimeMillis();
        Hashtable<String, ArrayList<Long>> runTimes = engine.batchRun(queryfile);
		//long end = System.currentTimeMillis();
		//System.out.print("Runtime: ");
        //int totalTime = 0;
        /*for(String query: runTimes.keySet()){
			String[] temp = query.split(" ");
			int sel = (Integer.parseInt(temp[temp.length - 1])) / 100;
			
			if(storeMethod.equals("RowStoreEng")){
				System.out.print(queryfile.replace("query/query_", "")+"_"+ sel + "|RowStore|");
                System.out.print(runTimes.get(query).get(0));
                for(int i=1; i<runTimes.get(query).size(); i++){
                    System.out.print(" "+runTimes.get(query).get(i));
                }
                System.out.println("|Avg:" + getMean(runTimes.get(query)) + "|Std:" + getStdDev(runTimes.get(query)) + "|Null:" + store.getNullRatio() + "|Id:" + store.getRowIdRatio());
			}else if(storeMethod.equals("ColStoreEng")){
				System.out.print(queryfile.replace("query/query_", "")+"_"+ sel + "|ColStore|");
                System.out.print(runTimes.get(query).get(0));
                for(int i=1; i<runTimes.get(query).size(); i++){
                    System.out.print(" "+runTimes.get(query).get(i));
                }
                System.out.println("|Avg:" + getMean(runTimes.get(query)) + "|Std:" + getStdDev(runTimes.get(query)));
			}else{
				String[] temp2 = layoutFile.split("\\|");
				System.out.print(queryfile.replace("query/query_", "")+"_"+ sel + "|RowColStore-" + temp2[temp2.length - 1] + "|");
                System.out.print(runTimes.get(query).get(0));
                for(int i=1; i<runTimes.get(query).size(); i++){
                    System.out.print(" "+runTimes.get(query).get(i));
                }
                System.out.println("|Avg:" + getMean(runTimes.get(query)) + "|Std:" + getStdDev(runTimes.get(query)) + "|Null:" + store.getNullRatio() + "|Id:" + store.getRowIdRatio());
			}

//            System.out.print(queryfile+"_"+ sel + "|" +  +runtime+" ");
            //totalTime += runtime;
        }*/
  //      System.out.println(totalTime);
    }
}
