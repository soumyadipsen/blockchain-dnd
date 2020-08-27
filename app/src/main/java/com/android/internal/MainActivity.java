package com.example.donotdisturb;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AddToBlockListActivity extends AppCompatActivity {

    private Button submit, viewAllBtn, consensusBtn, showBCBtn;
    private EditText phoneNUmberET;
    private TextView progperc;
    DatabaseHelper myDb;

//    ----------------------------------------
    private AsyncHttpServer server = new AsyncHttpServer();
    private AsyncServer mAsyncServer = new AsyncServer();

    SharedPreferences pref;
    boolean consensus_bool = false;

    Set<String> nodes = new HashSet<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_to_block_list);

        myDb = new DatabaseHelper(this);
        submit = (Button)findViewById(R.id.submitBtn);
        viewAllBtn = (Button)findViewById(R.id.viewAllBtn);
        consensusBtn = (Button)findViewById(R.id.consensusBtn);
        showBCBtn = (Button)findViewById(R.id.blockchainBtn);
        phoneNUmberET = (EditText)findViewById(R.id.BLnumberET);
        progperc = (TextView)findViewById(R.id.textView1);

        submit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                addDataDB();
                addDataBL();
            }
        });
        viewAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewAllDB();
            }
        });

        consensusBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new consensus().execute("");
            }
        });

        showBCBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getApplicationContext(), BlockChainActivity.class));
            }
        });

        //    ----------------------------------------
        pref = getSharedPreferences("settings", 0);
        //      Genesis Block
        if(pref.getString("blockchain", "-").equals("-")){
            JSONArray blockchain = new JSONArray();
            JSONObject block = new JSONObject();
            try{
                JSONObject transaction = new JSONObject();
                transaction.put("ph_no", "1111111111");
                block.put("index", 1);
                block.put("time", System.currentTimeMillis()/1000);
                block.put("nounce", 0);
                block.put("prehash", 0);
                block.put("transactions", new JSONArray().put(transaction));
                blockchain.put(block);
                pref.edit().putString("blockchain", blockchain.toString()).apply();

                JSONArray transactions = new JSONArray();
                transactions.put(transaction);
                pref.edit().putString("transactions", transactions.toString()).apply();
                System.out.println("blockchain "+blockchain.toString());
            }catch (JSONException e){
                e.printStackTrace();
            }
        }

        if(pref.getInt("difficulty", 0) == 0){
            pref.edit().putInt("difficulty", 2).apply();
        }

        new consensus().execute("");
    }

    private void addDataBL() {
        String number = phoneNUmberET.getText().toString();
        if(number==null || number.equals("") || !consensus_bool){
            Toast.makeText(this, "NO number", Toast.LENGTH_SHORT).show();
            return;
        }
        try{
            // Make a block
            JSONObject t = new JSONObject();
            t.put("ph_no", number);
            new_transaction(t);
               //  transaction queued
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void new_transaction(JSONObject transaction){
        try{
            JSONArray transactions = new JSONArray(pref.getString("transactions",""));
            transactions.put(transaction);
            pref.edit().putString("transactions", transactions.toString()).apply();
            new consensus().execute("mine");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        server.stop();
        mAsyncServer.stop();
    }

    @Override
    public void onResume() {
        super.onResume();
        startServer();
    }

    private void startServer() {
        server.get("/blockchain", new HttpServerRequestCallback() {
            @Override
            public void onRequest(AsyncHttpServerRequest request, AsyncHttpServerResponse response) {
                try{
                    response.code(200);
                    JSONObject res = new JSONObject();
                    JSONArray blockchain = new JSONArray(pref.getString("blockchain",""));
                    JSONArray transactions = new JSONArray(pref.getString("transactions",""));
                    res.put("blockchain", blockchain);
                    res.put("length", blockchain.length());
                    res.put("transactions", transactions);
                    response.send(res);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        server.listen(mAsyncServer, 8080);
    }

    private void viewAllDB() {
        Cursor res = myDb.getAllData();
        if(res.getCount() == 0){
            Toast.makeText(this, "No block list entries", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuffer buffer = new StringBuffer();
        while(res.moveToNext()){
            buffer.append("ID: "+res.getString(0)+"\t"+"NUMBER: "+res.getString(1));
        }

        Toast.makeText(this, buffer, Toast.LENGTH_SHORT).show();

    }
    private void addDataDB() {
        String number = phoneNUmberET.getText().toString();
        if(number==null){
            return;
        }

        boolean isInserted = myDb.insertData(number);
        if(isInserted){
            Toast.makeText(AddToBlockListActivity.this, "Number added to Database", Toast.LENGTH_SHORT).show();
        }else{
            Toast.makeText(AddToBlockListActivity.this, "Number NOT added to Database", Toast.LENGTH_SHORT).show();
        }
    }

    private class consensus extends AsyncTask<String, String, String>{
        String server_response;
        @Override
        protected String doInBackground(String... strings) {
            try{
                if(strings[0].equals("mine")){
                    for(String fullpath : nodes){
                        try{
                            URL url = new URL(fullpath);
                            HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();
                            int responseCode = urlConnection.getResponseCode();

                            if(responseCode == HttpURLConnection.HTTP_OK){
                                server_response = readStream(urlConnection.getInputStream());
                                Log.v("CURL", server_response);

                                JSONObject res = new JSONObject(server_response);
                                JSONArray nblockchain = res.getJSONArray("blockchain");
                                JSONArray ntransactions = res.getJSONArray("transactions");

                                int nlength = res.getInt("length");
                                nodes.add(fullpath);
                                JSONArray blockchain = new JSONArray(pref.getString("blockchain",""));
                                if(nlength>blockchain.length() && validate_chain(nblockchain)){
                                    pref.edit().putString("blockchain", nblockchain.toString()).apply();
                                    pref.edit().putString("blockList", ntransactions.toString()).apply();
                                }
                            }
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                    }

                    String lb = last_block();
                    long nounce = 0;
                    int difficulty = pref.getInt("difficulty", 2);
                    // Proof of Work
                    while (!hash(lb + String.valueOf(nounce)).substring(0, difficulty).equals(String.format("%0" + difficulty + "d", 0))) {
                        nounce++;
                    }
                    JSONArray blockchain = new JSONArray(pref.getString("blockchain",""));
                    JSONObject block = new JSONObject();
                    block.put("index", blockchain.length()+1);
                    block.put("time", System.currentTimeMillis()/1000);
                    JSONArray transactions = new JSONArray(pref.getString("transactions",""));
                    JSONObject numberObject = transactions.getJSONObject(transactions.length()-1);
                    block.put("transactions", numberObject);
                    block.put("nounce", nounce);
                    block.put("prehash", hash(lb));
                    blockchain.put(block);

                    pref.edit().putString("blockchain", blockchain.toString()).apply();
                }else{
                    WifiManager wm = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
                    String myip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
                    InetAddress host = InetAddress.getByName(String.valueOf(myip));
                    byte[] ip = host.getAddress();
                    //PING started
                    for(int i=1; i<255; i++) {
                        try {
                            ip[3] = (byte) i;
                            InetAddress address = InetAddress.getByAddress(ip);
                            publishProgress(address.toString().substring(1, address.toString().length()));
                            if (!address.toString().equals("/" + myip) && address.isReachable(100)) {
                                String fullpath = "http:/" + address.toString() + ":8080/blockchain";
                                System.out.println("PING: " + fullpath);
                                URL url = new URL(fullpath);
                                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                                int responseCode = urlConnection.getResponseCode();

                                if (responseCode == HttpURLConnection.HTTP_OK) {
                                    server_response = readStream(urlConnection.getInputStream());
                                    JSONObject res = new JSONObject(server_response);
                                    System.out.println("---------PING:"+address.toString()+"recieved JSON:"+res);
                                    JSONArray nblockchain = res.getJSONArray("blockchain");
                                    JSONArray ntransactions = res.getJSONArray("transactions");
                                    System.out.println("---------PING:"+address.toString()+"recieved JSON:"+ntransactions.toString());
                                    int nlength = res.getInt("length");
                                    nodes.add(fullpath);
                                    JSONArray blockchain = new JSONArray(pref.getString("blockchain", ""));
                                    if (nlength > blockchain.length() && validate_chain(nblockchain)) {
                                        pref.edit().putString("blockchain", nblockchain.toString()).apply();
                                        pref.edit().putString("transactions", ntransactions.toString()).apply();
                                    }
                                }
                            }
                        } catch (Exception e1) {
                            e1.printStackTrace();
                        }
                    }
                    // PING ended
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }



        @Override
        protected void onProgressUpdate(String ... values) {
            super.onProgressUpdate(values);
            progperc.setText("scanning " + values[0] + "");
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            consensus_bool = false;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            progperc.setText("");
            consensus_bool = true;
            Toast.makeText(getApplicationContext(), "Consensus Met", Toast.LENGTH_LONG).show();
        }


    }
    public static String formatString(String text){

        StringBuilder json = new StringBuilder();
        String indentString = "";

        for (int i = 0; i < text.length(); i++) {
            char letter = text.charAt(i);
            switch (letter) {
                case '{':
                case '[':
                    json.append("\n").append(indentString).append(letter).append("\n");
                    indentString = indentString + "\t";
                    json.append(indentString);
                    break;
                case '}':
                case ']':
                    indentString = indentString.replaceFirst("\t", "");
                    json.append("\n").append(indentString).append(letter);
                    break;
                case ',':
                    json.append(letter).append("\n").append(indentString);
                    break;

                default:
                    json.append(letter);
                    break;
            }
        }

        return json.toString();
    }
    // Converting InputStream to String
    private String readStream(InputStream in) {
        BufferedReader reader = null;
        StringBuffer response = new StringBuffer();
        try {
            reader = new BufferedReader(new InputStreamReader(in));
            String line = "";
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return response.toString();
    }

    private boolean validate_chain(JSONArray blockchain) throws JSONException {
        JSONObject curr, prev = blockchain.getJSONObject(0);
        int difficulty = pref.getInt("difficulty", 2);
        for(int i=1; i<blockchain.length(); ++i) {
            curr = blockchain.getJSONObject(i);

            if(!curr.getString("prehash").equals(hash(prev.toString()))) {
                return false;
            }
            Log.e("VALIDATE", "2");
            if(!hash(prev + String.valueOf(curr.getInt("nounce"))).substring(0, difficulty).equals(String.format("%0" + difficulty + "d", 0))){
                return false;
            }
            Log.e("VALIDATE", "3");
            prev = curr;
        }
        return true;
    }

    private String hash(String input) {
        try {
            char[] chars = input.toCharArray();
            Arrays.sort(chars);
            String sorted = new String(chars);
            Log.e("HASHINPUT", sorted);
            MessageDigest mDigest = MessageDigest.getInstance("SHA1");
            byte[] result = mDigest.digest(sorted.getBytes());
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < result.length; i++) {
                sb.append(Integer.toString((result[i] & 0xff) + 0x100, 16).substring(1));
            }
            Log.e("HASHINPUT", sb.toString());
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        int difficulty = pref.getInt("difficulty", 2);
        return String.format("%0" + difficulty + "d", 0);
    }

    private String last_block() throws JSONException {
        JSONArray blockchain = new JSONArray(pref.getString("blockchain", ""));
        return blockchain.getJSONObject(blockchain.length()-1).toString();
    }
}
