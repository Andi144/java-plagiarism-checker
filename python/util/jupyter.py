from tkinter import Tk, filedialog

import ipywidgets as widgets  # https://ipywidgets.readthedocs.io/en/stable/index.html


# Adapted from https://codereview.stackexchange.com/a/229889
class SelectFileButton(widgets.Button):
    """A file widget that leverages tkinter.filedialog."""
    
    def __init__(self):
        super().__init__()
        self._set_default(self)
        self.on_click(self.select_files)
    
    @staticmethod
    def _set_default(button: widgets.Button):
        button.description = "Select File"
        button.icon = "square-o"
        button.style.button_color = "orange"
        button.file = None
    
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
        else:
            SelectFileButton._set_default(button)
