from tkinter import Tk, filedialog

import ipywidgets as widgets  # https://ipywidgets.readthedocs.io/en/stable/index.html


# Adapted from https://codereview.stackexchange.com/a/229889
class SelectFileButton(widgets.Button):
    """A file widget that leverages tkinter.filedialog."""
    
    def __init__(self):
        super().__init__()
        self.description = "Select File"
        self.icon = "square-o"
        self.style.button_color = "orange"
        self.on_click(self.select_files)
        self.file = None
    
    @staticmethod
    def select_files(button: widgets.Button):
        # Create Tk root
        root = Tk()
        # Hide the main window
        root.withdraw()
        # Raise the root to the top of all windows
        root.call("wm", "attributes", ".", "-topmost", True)
        button.file = filedialog.askopenfilename(filetypes=[("CSV files", "*.csv")])
        
        if button.file:
            button.description = "File Selected"
            button.icon = "check-square-o"
            button.style.button_color = "lightgreen"
