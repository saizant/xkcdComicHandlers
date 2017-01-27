package es.schooleando.xkcdcomichandlers;

import android.app.Activity;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by ruben on 5/01/17.
 */

public class ComicManager {

    private HandlerThread downloadHandlerThread;
    private static DownloadHandler downloadHandler; // Funcionará asociado al Worker Thread (HandlerThread)
    //private Handler imageHandler;            // Funcionará asociado al UI Thread
    private ImageHandler imageHandler;            // Funcionará asociado al UI Thread
    private static boolean timerActive;             // Controlamos si el timer está activo o no
    private static int seconds;                     // Segundos del timer

    private static int lastComic = -1;

    //Vistas del layout
    private static ImageView imageView;
    private static ProgressBar progressBar;

    //Referencia débil a la MainActivity para evitar Memory Leak
    private static WeakReference<Activity> activity;

    //Constantes para resultados
    public static final int LOAD_IMAGE = 0;
    public static final int DOWNLOAD = 1;
    public static final int PROGRESS = 2;
    public static final int ERROR = 3;


    public ComicManager(Activity activity, int secondsTimer) {

        //Recogemos la referencia a la Activity
        this.activity = new WeakReference<>(activity);

        // Aquí inicializamos el HandlerThread y el DownloadHandler usando el Looper de HandlerThread
        downloadHandlerThread = new HandlerThread("ComicHT");
        downloadHandlerThread.start();

        // Inicializamos la imageHandler a partir de la static inner class definida posteriormente, asociandola al UI Looper
        imageHandler = new ImageHandler();
        //imageHandler = new ImageHandler(Looper.getMainLooper());
        downloadHandler = new DownloadHandler(downloadHandlerThread.getLooper(), activity, imageHandler);

        // Inicializamos la temporalización
        startTimer(secondsTimer);

        //Enganchar vistas del layout
        progressBar = (ProgressBar)this.activity.get().findViewById(R.id.progressBar);
        imageView = (ImageView)this.activity.get().findViewById(R.id.imageView);

        //Al pinchar en la imagen:
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                downloadComic();
            }
        });

        //Llamada al método start():
        start();
    }

    //MÉTODOS:


    public void start() {
        // Arrancamos el HandlerThread.
        if (!downloadHandlerThread.isAlive()) {
            downloadHandlerThread.start();
        }

        // llamamos a downloadComic una vez
        downloadComic();
    }

    public void stop() {
        //Ocultar barra de progreso
        progressBar.setVisibility(View.INVISIBLE);

        // Enviamos un Toast de que se está parando la aplicación
        Toast.makeText(activity.get(), "Parando la aplicación...", Toast.LENGTH_LONG).show();

        // Desactivamos el timer para que evite enviar mensajes a un HandlerThread que ya no existirá.
        // Paramos el HandlerThread, limpiando su cola de mensajes y esperando a que acabe su trabajo activo si lo tiene
        stopTimer();
    }

    public static void downloadComic() {
        //Mostrar barra de progreso
        progressBar.setVisibility(View.VISIBLE);

        // enviamos un mensaje para descargar un Comic (cuando pulsemos sobre el imageView)
        Message mensaje = downloadHandler.obtainMessage();
        mensaje.what = DOWNLOAD;

        if (lastComic < 0) {
            mensaje.obj = "http://xkcd.com/info.0.json";
        } else {
            int aleatorio = ThreadLocalRandom.current().nextInt(1, lastComic +1);
            mensaje.obj = "http://xkcd.com/" + aleatorio + "/info.0.json";
        }

        downloadHandler.sendMessageDelayed(mensaje, seconds*1000);
    }

    public void startTimer(int segundos) {
        // activamos el timer y configuramos el timer
        timerActive = true;
        seconds = segundos;
    }

    public void stopTimer() {
        // desactivamos el timer
        timerActive = false;
        // limpiamos mensajes de Timer en el HandlerThread
        downloadHandler.removeMessages(DOWNLOAD);
    }

    // Interfaz privada


    // Aquí declararemos una static inner class Handler
     public static class ImageHandler extends Handler {

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case (LOAD_IMAGE):
                    //Obtenemos la URI del archivo temporal y cargamos el imageView
                    String[] datos = ((String)msg.obj).split("\\|");

                    //Si el Comic descargado es el último, se descargará el siguiente de forma aleatoria
                    if (lastComic < 0) {
                        lastComic = Integer.parseInt(datos[1]);
                    }

                    String urlString = datos[0];
                    progressBar.setVisibility(View.INVISIBLE);
                    File fichero = new File(urlString);

                    if (fichero.exists()) {
                        //Cargar imagen
                        imageView.setImageBitmap(BitmapFactory.decodeFile(fichero.getAbsolutePath()));
                    } else {
                        Toast.makeText(activity.get(), "El fichero no existe", Toast.LENGTH_SHORT).show();
                    }

                    //si está activo el timer posteriormente enviaremos un mensaje retardado de DOWNLOAD_COMIC al HandlerThread, solo si está activo el Timer.
                    if (timerActive) {
                        downloadComic();
                    }

                    break;

                case (PROGRESS):
                    //actualizaremos el progressBar
                    int porcentaje = (int)msg.obj;
                    progressBar.setIndeterminate(porcentaje < 0);
                    progressBar.setProgress(porcentaje);

                    break;

                case (ERROR):
                    //mostraremos un Toast del error.Cancelamos el Timer para evitar errores posteriores
                    progressBar.setVisibility(View.INVISIBLE);
                    Toast.makeText(activity.get(), (String)msg.obj, Toast.LENGTH_LONG).show();
                    if (timerActive) {
                        downloadComic();
                    }

                    break;

                default:
                    //Importante procesar el resto de mensajes:
                    super.handleMessage(msg);
            }
        }
    }
}
