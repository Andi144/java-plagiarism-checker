import warnings
from collections import defaultdict
from typing import Callable

import networkx as nx
import pandas as pd
import plotly.graph_objects as go
from bokeh.io import show
from bokeh.models import Circle, MultiLine, HoverTool, EdgesAndLinkedNodes, TapTool, CustomJS, EdgesOnly
from bokeh.plotting import figure
from bokeh.plotting import from_networkx
from tqdm.auto import tqdm

from .detection import metrics


def get_rule_priority(row: pd.Series) -> int:
    # Follows same rules as "is_plag" plagiarism detection
    if row["rule0"]:
        return 3
    if row["rule1"]:
        return 2
    if row["rule2"]:
        return 1
    if row["rule3"]:
        return 0
    raise ValueError("none of the 'rule' columns was True (did you include non-plagiarism examples?)")


def create_plagiarism_graph(
        plags_df: pd.DataFrame,
        priority_getter: Callable[[pd.Series], int] = get_rule_priority,
        show_progress: bool = False
) -> nx.Graph:
    """
    Creates a plagiarism graph.
    
    :param plags_df: The dataframe containing plagiarism entries. It must contain the columns "student1" and "student2".
    :param priority_getter: This function is used to assign the attribute "priority" to every edge between "student1"
        and "student2" (every row in `plags_df`). If `plags_df` contains multiple entries for the same two students,
        then the higher priority will be used as the attribute value.
    :param show_progress: Whether to show a progress bar.
    :return: A networkx plagiarism graph.
    """
    if len(plags_df) == 0:
        warnings.warn("'plags_df' is empty, so the graph will be empty as well")
    
    graph = nx.Graph()
    id_counter = 0
    ids = dict()
    
    for i in tqdm(range(len(plags_df)), disable=not show_progress):
        row = plags_df.iloc[i]
        if row.student1 not in ids:
            ids[row.student1] = id_counter
            id_counter += 1
        if row.student2 not in ids:
            ids[row.student2] = id_counter
            id_counter += 1
        student1_id = ids[row.student1]
        student2_id = ids[row.student2]
        
        if not graph.has_node(student1_id):
            graph.add_node(student1_id, name=row.student1)
        if not graph.has_node(student2_id):
            graph.add_node(student2_id, name=row.student2)
        
        priority = priority_getter(row)
        if not graph.has_edge(student1_id, student2_id):
            graph.add_edge(student1_id, student2_id, priority=priority, data=plags_df.iloc[i:i + 1])
        else:
            current_priority = graph.edges[student1_id, student2_id]["priority"]
            if priority > current_priority:
                graph.edges[student1_id, student2_id]["priority"] = priority
            
            data = graph.edges[student1_id, student2_id]["data"]
            added = pd.concat([data, plags_df.iloc[i:i + 1]])
            graph.edges[student1_id, student2_id]["data"] = added
    
    return graph


def plot_bokeh_plagiarism_graph(
        graph: nx.Graph,
        priority_to_color: dict[int, str] = None,
        weight: float = 0.01,
        graph_layout: callable = nx.spring_layout,
        graph_layout_kwargs: dict = None
):
    """
    Plots the specified plagiarism graph using bokeh.
    
    :param graph: The networkx plagiarism graph created with `graph.create_plagiarism_graph`.
    :param priority_to_color: Mapping from edge priority to edge color. For each unique priority in `graph`, there must
        be a corresponding (not necessarily unique) color.
    :param weight: Weight used for the graph layout.
    :param graph_layout: The graph layout function.
    :param graph_layout_kwargs: Keyword arguments that are passed when calling `graph_layout`.
    """
    if priority_to_color is None:
        priority_to_color = {
            3: "red",
            2: "blue",
            1: "green",
            0: "black"
        }
    
    if graph_layout_kwargs is None:
        graph_layout_kwargs = dict(seed=0)
    
    # cannot store pd.DataFrame afterward in bokeh representation, so replace it with built-in data
    graph = graph.copy()
    n_plags = sum(len(graph.edges[edge]["data"]) for edge in graph.edges())
    for edge in graph.edges():
        data = graph.edges[edge]["data"]
        graph.edges[edge]["data"] = None
        # TODO: hard-coded (should be parameterized to not rely that df/data contains these columns)
        for col in ["student1", "student2", "file1", "file2"]:
            graph.edges[edge][col] = data[col]
        for m in metrics:
            # G.edges[edge][m] = data[m].to_list()
            # TODO: only formatting already here because I do not know how to do it with bokeh hover_formatters (see
            #  custom formatters documentation)
            graph.edges[edge][m] = ", ".join(f"{x:.3f}" for x in data[m])
        for t in ["type1", "type2"]:
            graph.edges[edge][t] = ", ".join(data[t])
        graph.edges[edge]["color"] = priority_to_color[graph.edges[edge]["priority"]]
        graph.edges[edge]["weight"] = weight  # Attribute for (nx) layout
    
    # create plot figure
    plot = figure(
        tools="pan,wheel_zoom,save,reset,tap",
        active_scroll="wheel_zoom",
        title=f"Plagiarism Clusters ({n_plags} plagiarisms between {len(graph.nodes())} students)",
        x_axis_location=None,
        y_axis_location=None,
    )
    
    # add graph
    graph_renderer = from_networkx(graph, graph_layout, **graph_layout_kwargs)
    plot.renderers.append(graph_renderer)
    
    # normal render
    graph_renderer.node_renderer.glyph = Circle(size=15, fill_color="skyblue")
    graph_renderer.edge_renderer.glyph = MultiLine(line_color="color", line_width=2)
    
    # hover render
    graph_renderer.node_renderer.hover_glyph = Circle(size=25, fill_color="#ffcc00")
    graph_renderer.edge_renderer.hover_glyph = MultiLine(line_color="#ffcc00", line_width=5)
    
    # selection render
    graph_renderer.node_renderer.selection_glyph = Circle(size=25, fill_color="#00ff00")
    graph_renderer.edge_renderer.selection_glyph = MultiLine(line_color="#00ff00", line_width=5)
    
    # hover information
    graph_renderer.inspection_policy = EdgesOnly()
    plot.add_tools(HoverTool(
        tooltips=
        [("type1", "@type1"), ("type2", "@type2")] +
        [(m, f"@{m}") for m in metrics] +
        [("student1", "@student1"), ("student2", "@student2")],
        # formatters={f"@{m}": "printf" for m in metrics}
    ))
    
    # selection information
    graph_renderer.selection_policy = EdgesAndLinkedNodes()
    
    # Cannot use tuple of ints as keys, since we then could not get these keys in JavaScript
    edge_data = dict()
    for e1, e2 in graph.edges:
        if e1 not in edge_data:
            edge_data[e1] = dict()
        edge_data[e1][e2] = (graph.edges[e1, e2]["file1"], graph.edges[e1, e2]["file2"])
    
    callback = CustomJS(args=dict(edge_data=edge_data), code="""
console.log(cb_data.source.selected.indices)
console.log(edge_data)
const key0 = cb_data.source.selected.indices[0]
const key1 = cb_data.source.selected.indices[1]
const result = edge_data.get(key0).get(key1)
const files1 = result[0]
const files2 = result[1]
console.log(files1[0]) // edge might contain multiple files, for now, just pick the first
console.log(files2[0]) // edge might contain multiple files, for now, just pick the first
""")
    tap_tool = plot.select(type=TapTool)
    tap_tool.callback = callback
    
    # grid settings
    plot.grid.visible = False
    
    # display
    show(plot)


def create_midpoints(x0: float, y0: float, x1: float, y1: float, n_midpoints: int) -> tuple[list[float], list[float]]:
    n_pieces = n_midpoints + 1
    diff_x = (x1 - x0) / n_pieces
    diff_y = (y1 - y0) / n_pieces
    xs = []
    ys = []
    for i in range(1, n_midpoints + 1):
        xs.append(x0 + diff_x * i)
        ys.append(y0 + diff_y * i)
    return xs, ys


def create_hover_text(data: pd.DataFrame) -> str:
    # data contains multiple entries in case there are multiple type matches. The student names, however, are the same,
    # so just pick the first one.
    student1 = data["student1"].iloc[0]
    student2 = data["student2"].iloc[0]
    # Formatting relies on monospace font
    type1s = data["type1"]
    type2s = data["type2"]
    max_type_lens = []
    for t1, t2 in zip(type1s, type2s):
        max_type_lens.append(max(len(t1), len(t2)))
    metric_texts = []
    for m in metrics:
        assert len(data[m]) == len(max_type_lens)
        metric_texts.append(
            f"{m}: " + " ".join(f"<b>{x:{max_type_len}.3f}</b>" for x, max_type_len in zip(data[m], max_type_lens)))
    return "<br>".join([
        f"<b>{student1}</b>: " + " ".join(
            f"<b>{t:>{max_type_len}}</b>" for t, max_type_len in zip(type1s, max_type_lens)),
        f"<b>{student2}</b>: " + " ".join(
            f"<b>{t:>{max_type_len}}</b>" for t, max_type_len in zip(type2s, max_type_lens)),
        "<br>".join(metric_texts)
    ])


def create_plotly_plagiarism_graph(
        graph: nx.Graph,
        priority_edge_visualization_kwargs: dict = None,
        node_visualization_kwargs: dict = None,
        figure_layout_kwargs: dict = None,
        weight: float = 0.01,
        graph_layout: callable = nx.spring_layout,
        graph_layout_kwargs: dict = None,
        n_midpoints: int = 10,
        edge_click_callback: Callable[[pd.DataFrame], None] = None
) -> go.FigureWidget:
    """
    Creates a plotly `FigureWidget` containing the specified plagiarism graph.
    
    :param graph: The networkx plagiarism graph created with `graph.create_plagiarism_graph`.
    :param priority_edge_visualization_kwargs: Mapping from priority to keyword arguments that are passed when creating
        the respective `go.Scatter(line=priority_edge_visualization_kwargs[priority])` for edge visualization.
    :param node_visualization_kwargs: Keyword arguments that are passed when creating the node markers of the main graph
        with `go.Scatter(marker=node_visualization_kwargs)`.
    :param figure_layout_kwargs: Keyword arguments that are passed when creating a `go.FigureWidget` instance.
    :param weight: Weight used for the graph layout.
    :param graph_layout: The graph layout function.
    :param graph_layout_kwargs: Keyword arguments that are passed when calling `graph_layout`.
    :param n_midpoints: The number of (invisible) nodes to create along each edge (evenly spaced).
    :param edge_click_callback: The function to invoke when clicking on an edge midpoint.
    :return: A plotly `FigureWidget` containing the specified plagiarism graph.
    """
    if priority_edge_visualization_kwargs is None:
        priority_edge_visualization_kwargs = {
            3: dict(width=2, color="#f00"),
            2: dict(width=2, color="#00f"),
            1: dict(width=2, color="#0f0"),
            0: dict(width=2, color="#000")
        }
    
    if node_visualization_kwargs is None:
        node_visualization_kwargs = dict(size=15, color="#fa0", line=dict(width=1, color="#000"))
    
    if figure_layout_kwargs is None:
        n_plags = sum(len(graph.edges[edge]["data"]) for edge in graph.edges())
        figure_layout_kwargs = dict(
            title=dict(
                text=f"Plagiarism Clusters ({n_plags} plagiarisms between {len(graph.nodes())} students)",
                x=0,
                y=1
            ),
            showlegend=False,
            width=1000,
            height=700,
            margin=dict(l=0, r=0, t=20, b=0),
            xaxis=dict(visible=False),
            yaxis=dict(visible=False),
            modebar=dict(remove=["lasso", "select"])
        )
    
    if graph_layout_kwargs is None:
        graph_layout_kwargs = dict(seed=0)
    
    graph = graph.copy()
    groups = defaultdict(list)
    for edge in graph.edges():
        graph.edges[edge]["weight"] = weight  # Attribute for (nx) layout
        groups[graph.edges[edge]["priority"]].append(edge)
    
    pos = graph_layout(graph, **graph_layout_kwargs)
    
    graph_traces = []
    midpoint_xs, midpoint_ys, midpoint_texts = [], [], []
    midpoint_index = 0
    midpoint_to_data = dict()  # Mapping from midpoint index to networkx graph edge data
    
    for priority, edges in groups.items():
        # Create a separate trace for each priority group to color them differently
        graph_xs, graph_ys, graph_texts = [], [], []
        
        for e1, e2 in edges:
            x0, y0 = pos[e1]
            x1, y1 = pos[e2]
            graph_xs.extend([x0, x1, None])
            graph_ys.extend([y0, y1, None])
            graph_texts.extend([graph.nodes[e1]["name"], graph.nodes[e2]["name"], None])
            
            xs, ys = create_midpoints(x0, y0, x1, y1, n_midpoints=n_midpoints)
            midpoint_xs.extend(xs)
            midpoint_ys.extend(ys)
            data = graph.edges[e1, e2]["data"]
            text = create_hover_text(data)
            midpoint_texts.extend([text] * n_midpoints)
            
            for _ in range(n_midpoints):
                midpoint_to_data[midpoint_index] = data
                midpoint_index += 1
        
        graph_trace = go.Scatter(
            x=graph_xs,
            y=graph_ys,
            hoverinfo="text",
            hovertext=graph_texts,
            mode="lines+markers",
            line=priority_edge_visualization_kwargs[priority],
            marker=node_visualization_kwargs
        )
        graph_traces.append(graph_trace)
    
    midpoint_trace = go.Scatter(
        x=midpoint_xs,
        y=midpoint_ys,
        hoverinfo="text",
        hovertext=midpoint_texts,
        hoverlabel=dict(align="right", font=dict(family="Courier New"), bgcolor="#fff"),
        mode="markers",
        marker=dict(opacity=0)
    )
    
    fig = go.FigureWidget(data=graph_traces + [midpoint_trace], layout=figure_layout_kwargs)
    
    if edge_click_callback is not None:
        # noinspection PyUnusedLocal
        def midpoint_trace_click_callback(trace, points, selector):
            # Callback is triggered for every node click event, but points.point_inds are only populated if
            # points.trace_index match the trace for which this callback was registered
            if points.point_inds:
                if len(points.point_inds) > 1:
                    raise ValueError("only a single midpoint can be selected")
                edge_click_callback(midpoint_to_data[points.point_inds[0]])
        
        # Must use fig.data[i] instead of trace object (callback does not work otherwise)
        fig.data[-1].on_click(midpoint_trace_click_callback)
    
    return fig
