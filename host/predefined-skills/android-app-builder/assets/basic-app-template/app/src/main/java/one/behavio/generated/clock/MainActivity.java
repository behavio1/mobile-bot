package one.behavio.generated.clock;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(48, 48, 48, 48);
        content.setBackgroundColor(Color.rgb(248, 247, 242));

        TextView label = new TextView(this);
        label.setText("Aktualny czas");
        label.setTextColor(Color.rgb(91, 105, 101));
        label.setTextSize(18);

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("HH:mm:ss");
        clock.setFormat24Hour("HH:mm:ss");
        clock.setTextColor(Color.rgb(23, 74, 64));
        clock.setTextSize(54);
        clock.setGravity(Gravity.CENTER);
        clock.setContentDescription("Aktualny czas");

        TextView footer = new TextView(this);
        footer.setText("Aplikacja utworzona przez agenta Programista");
        footer.setTextColor(Color.rgb(91, 105, 101));
        footer.setTextSize(14);

        content.addView(label);
        content.addView(clock);
        content.addView(footer);
        setContentView(content);
    }
}
