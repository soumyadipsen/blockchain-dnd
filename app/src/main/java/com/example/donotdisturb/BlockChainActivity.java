package com.example.donotdisturb;

import androidx.appcompat.app.AppCompatActivity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static com.example.donotdisturb.AddToBlockListActivity.formatString;

public class BlockChainActivity extends AppCompatActivity {

    SharedPreferences pref;
    TextView display;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_block_chain);

        pref = getSharedPreferences("settings", 0);
        display = (TextView) findViewById(R.id.textView2);
        display.setMovementMethod(new ScrollingMovementMethod());
        display.setText("" + formatString(pref.getString("blockchain", "{}")) + "");

//        try {
//            showNumberList();
//        } catch (JSONException e) {
//            e.printStackTrace();
//        }
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

    public void showNumberList() throws JSONException {
        StringBuilder res = new StringBuilder("");
        JSONArray transactions = new JSONArray(pref.getString("transactions",""));
        if(transactions!=null && transactions.length()>0){
            for (int i = 0; i < transactions.length(); i++) {
                JSONObject obj = transactions.getJSONObject(i);
                String number = obj.getString("ph_no");
                res.append(i+":\t"+number+"\n");
            }
        }
        display.setText(res);
    }
}
