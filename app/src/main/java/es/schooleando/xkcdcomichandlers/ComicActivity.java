package es.schooleando.xkcdcomichandlers;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

public class ComicActivity extends AppCompatActivity {

    //Vistas del layout
    private Button tiempoBtn, salirBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializamos el Comic
        final ComicManager manager = new ComicManager(this, 5);

        //Enganchar vistas del layout
        tiempoBtn = (Button)findViewById(R.id.tiempoBtn);
        salirBtn = (Button)findViewById(R.id.salirBtn);

        // Aquí faltará añadir Listeners para:
        // un botón de activar/desactivar Timer
        // un botón para salir de la App
        tiempoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (tiempoBtn.getText().toString().equalsIgnoreCase("Parar")) {
                    tiempoBtn.setText("Continuar");
                    manager.stop();
                } else {
                    tiempoBtn.setText("Parar");
                    manager.start();
                }
            }
        });

        salirBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                manager.stop();
                ComicActivity.this.finish();
                System.exit(0);
            }
        });
    }

}
