package com.dynamo.cr.contenteditor.manipulator;

public interface IManipulator
{
    /**
     * Called on mouse down event
     * @param context Context
     */
    void mouseDown(ManipulatorContext context);

    /**
     * Called on mouse up event
     * @param context Context
     */

    void mouseUp(ManipulatorContext context);

    /**
     * Called on mouse move event
     * @param context Context
     */
    void mouseMove(ManipulatorContext context);

    /**
     * Draw manipulator
     * @param gl
     */
    void draw(ManipulatorDrawContext context);

    /**
     * Set manipulator name
     * @param Name
     */
    void setName(String mName);

    /**
     * Get manipulator name
     * @return Name
     */
    String getName();

    /**
     * Called on key pressed
     * @param ctx Context
     */
    void keyPressed(ManipulatorContext ctx);
}
