/**
 * 
 */
package com.google.android.diskusage;


class Cursor {
	FileSystemEntry root;
	FileSystemEntry position;
	long top;
	int depth;
	
	Cursor(FileSystemEntry root) {
		this.root = root;
		
		if (root.children == null || root.children.length == 0) {
		  throw new RuntimeException("no place for position");
		}
		position = root.children[0]; 
		depth = 0;
		top = 0;
	}
	
	void down(FileSystemView view) {
		FileSystemEntry newCursor = position.getNext();
		if (newCursor == position) return;
		view.invalidate(this);
		top += position.size;
		position = newCursor;
		view.invalidate(this);
		view.titleNeedUpdate = true;
	}
	
	void up(FileSystemView view) {
		FileSystemEntry newCursor = position.getPrev();
		if (newCursor == position) return;
		view.invalidate(this);
		top -= newCursor.size;
		position = newCursor;
		view.invalidate(this);
        view.titleNeedUpdate = true;
	}
	
	void right(FileSystemView view) {
		if (position.children == null) return;
		if (position.children.length == 0) return;
		view.invalidate(this);
		position = position.children[0];
		depth++;
		// Log.d("Sample", "position depth = " + depth);
		view.invalidate(this);
        view.titleNeedUpdate = true;
	}
	
	boolean left(FileSystemView view) {
		if (position.parent == root) return false;
		view.invalidate(this);
		position = position.parent;
		top = root.getOffset(position);
		depth--;
		// Log.d("Sample", "position depth = " + depth);
		view.invalidate(this);
        view.titleNeedUpdate = true;
		return true;
	}
	
	void set(FileSystemView view, FileSystemEntry newpos) {
		view.invalidate(this);
		position = newpos;
		depth = root.depth(position) - 1;
		// Log.d("Sample", "position depth = " + depth);
		top = root.getOffset(position);
		view.invalidate(this);
        view.titleNeedUpdate = true;
	}
	
	void refresh(FileSystemView view) {
	  set(view, position);
	}
}