package com.dynamo.cr.contenteditor.editors;

import javax.media.opengl.GL;

import com.dynamo.cr.contenteditor.scene.Node;

public class DrawContext
{
    public DrawContext(GL gl, Node[] selected_nodes)
    {
        m_GL = gl;
        m_SelectedNodes = selected_nodes;
    }
    public GL m_GL;
    public Node[] m_SelectedNodes;

    public boolean isSelected(Node node) {
        for (Node n : m_SelectedNodes) {

            Node tmp = node;
            while (tmp != null) {
                if (tmp == n)
                    return true;
                else
                    tmp = tmp.getParent();
            }
        }
        return false;
    }
}
