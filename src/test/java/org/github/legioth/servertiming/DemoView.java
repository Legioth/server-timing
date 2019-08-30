package org.github.legioth.servertiming;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.router.Route;

import static org.github.legioth.servertiming.DemoHelper.*;

import org.github.legioth.servertiming.ServerTiming.Stopwatch;

@Route("")
public class DemoView extends VerticalLayout {
    private final boolean cacheMiss = true;

    public DemoView() {
        Button slowButton = new Button("Say hello", ServerTiming.wrapListener("clickListener", event -> {
            if (cacheMiss) {
                ServerTiming.set("cacheMiss");

                ServerTiming.run("loadData", () -> sleep(250));
            }

            // Some extra slowness just because :)
            sleep(250);

            Stopwatch addTextStopwatch = ServerTiming.start("addText");
            add(new Text("Hello there"));
            addTextStopwatch.complete();
        }));

        Button gridButton = new Button("Show Grid", event -> {
            Grid<String> grid = new Grid<>();
            grid.addColumn(value -> value);

            ListDataProvider<String> slowDataProvider = slowDataProvider("Person 1", "Person 2", "Person 3");

            grid.setDataProvider(ServerTiming.wrapDataProvider("personGrid", slowDataProvider));

            add(grid);
        });

        add(slowButton, gridButton);
    }
}
