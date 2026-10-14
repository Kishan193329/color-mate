package com.colourmate.paintbynumber.OpenGL;

import android.content.Context;
import android.opengl.GLES20;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Shader {
    private static final String FRAGMENT_POINT_SHADER_FILENAME = "fragPoint.glsl";
    private static final String FRAGMENT_SHADER_FILENAME = "frag.glsl";
    private static final String VERTEX_POINT_SHADER_FILENAME = "vertPoint.glsl";
    private static final String VERTEX_SHADER_FILENAME = "vert.glsl";
    private final int attribPosition;
    private final int attribPositionPoint;
    private final int attribTexCoord;
    private final int attribTexCoordPoint;
    private int program = GLES20.glCreateProgram();
    private final int programPoint;
    private final int uniformAlpha;
    private final int uniformAlphaPoint;
    private final int uniformMode;
    private final int uniformModePoint;
    private final int uniformMvpMatrix;
    private final int uniformMvpMatrixPoint;
    private final int uniformScaleFactor;
    private final int uniformSelectedGroup;
    private final int uniformShowHint;
    private final int uniformShowMistookColors;
    private final int uniformTextureBase;
    private final int uniformTextureBlueprint;
    private final int uniformTextureColors;
    private final int uniformTextureNumber;
    private final int uniformTexturePaint;

    public Shader(Context context) {

        // -------------------------------------------------------------
        // Load main shader (texture rendering)
        // -------------------------------------------------------------
        int vertexShader    = loadShader(GLES20.GL_VERTEX_SHADER,   VERTEX_SHADER_FILENAME, context);
        int fragmentShader  = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_FILENAME, context);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);

        // Attribute locations
        attribPosition  = 0;
        attribTexCoord  = 1;

        GLES20.glBindAttribLocation(program, attribPosition, "a_vertexPos");
        GLES20.glBindAttribLocation(program, attribTexCoord, "a_texCoord");

        // Link program
        GLES20.glLinkProgram(program);
        checkProgramLinkStatus(program, "MainProgram");

        // Cache uniform locations
        uniformMvpMatrix          = GLES20.glGetUniformLocation(program, "u_mvpMatrix");
        uniformTextureBase        = GLES20.glGetUniformLocation(program, "u_textureBase");
        uniformTexturePaint       = GLES20.glGetUniformLocation(program, "u_texturePaint");
        uniformTextureColors      = GLES20.glGetUniformLocation(program, "u_textureColors");
        uniformTextureBlueprint   = GLES20.glGetUniformLocation(program, "u_textureBlueprint");

        uniformMode               = GLES20.glGetUniformLocation(program, "u_mode");
        uniformSelectedGroup      = GLES20.glGetUniformLocation(program, "u_selectedGroup");
        uniformAlpha              = GLES20.glGetUniformLocation(program, "u_alpha");

        uniformShowMistookColors  = GLES20.glGetUniformLocation(program, "showMistookColors");
        uniformShowHint           = GLES20.glGetUniformLocation(program, "showHint");


        // -------------------------------------------------------------
        // Load point shader (numbers & grid rendering)
        // -------------------------------------------------------------
        int pointVertexShader   = loadShader(GLES20.GL_VERTEX_SHADER,   VERTEX_POINT_SHADER_FILENAME, context);
        int pointFragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_POINT_SHADER_FILENAME, context);

        programPoint = GLES20.glCreateProgram();
        GLES20.glAttachShader(programPoint, pointVertexShader);
        GLES20.glAttachShader(programPoint, pointFragmentShader);

        // Attribute locations
        attribPositionPoint = 0;
        attribTexCoordPoint = 1;

        GLES20.glBindAttribLocation(programPoint, attribPositionPoint, "a_vertexPos");
        GLES20.glBindAttribLocation(programPoint, attribTexCoordPoint, "a_texCoord");

        // Link program
        GLES20.glLinkProgram(programPoint);
        checkProgramLinkStatus(programPoint, "PointProgram");

        // Cache uniform locations
        uniformMvpMatrixPoint = GLES20.glGetUniformLocation(programPoint, "u_mvpMatrix");
        uniformAlphaPoint     = GLES20.glGetUniformLocation(programPoint, "u_alpha");
        uniformScaleFactor    = GLES20.glGetUniformLocation(programPoint, "scaleFactor");
        uniformTextureNumber  = GLES20.glGetUniformLocation(programPoint, "u_textureNumber");
        uniformModePoint      = GLES20.glGetUniformLocation(programPoint, "u_mode");
    }

    private void checkProgramLinkStatus(int program, String tag) {
        int[] status = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);

        if (status[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(program);
            Log.e("Shader", "Failed to link " + tag + ": " + log);
            GLES20.glDeleteProgram(program);
        }
    }


    public void setUniformMvp(float[] fArr) {
        GLES20.glUniformMatrix4fv(uniformMvpMatrix, 1, false, fArr, 0);
    }

    /**
     * Sets painting mode in the main shader.
     *
     * @param paintTexture      User-painted pixels texture
     * @param blueprintTexture  Blueprint (correct numbers) texture
     * @param colorsTexture     Color lookup texture
     * @param alpha             Alpha blending factor (0–1)
     * @param showHint          If true, highlight the target region
     * @param showMistakes      If true, highlight incorrectly painted areas
     */
    public void setPaintingMode(
            Texture paintTexture,
            Texture blueprintTexture,
            Texture colorsTexture,
            float alpha,
            boolean showHint,
            boolean showMistakes
    ) {
        // Select main shader program
        GLES20.glUseProgram(program);

        // --- Texture 0 : Paint Layer ---------------------------------------------------
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(
                GLES20.GL_TEXTURE_2D,
                paintTexture != null ? paintTexture.glId : 0
        );
        GLES20.glUniform1i(uniformTexturePaint, 0);

        // --- Texture 1 : Blueprint Layer -----------------------------------------------
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(
                GLES20.GL_TEXTURE_2D,
                blueprintTexture != null ? blueprintTexture.glId : 0
        );
        GLES20.glUniform1i(uniformTextureBlueprint, 1);

        // --- Texture 2 : Colors Lookup -------------------------------------------------
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(
                GLES20.GL_TEXTURE_2D,
                colorsTexture != null ? colorsTexture.glId : 0
        );
        GLES20.glUniform1i(uniformTextureColors, 2);

        // --- Shader Mode: Painting -----------------------------------------------------
        GLES20.glUniform1i(uniformMode, 0);

        // --- Alpha ---------------------------------------------------------------------
        GLES20.glUniform1f(uniformAlpha, alpha);

        // --- Mistakes Highlighting (wrong pixels) -------------------------------------
        GLES20.glUniform1f(uniformShowMistookColors, showMistakes ? 1f : 0f);

        // --- Hint Highlighting (unpainted correct region) ------------------------------
        GLES20.glUniform1f(uniformShowHint, showHint ? 1f : 0f);
    }

    /**
     * Sets the regular (base) texture mode for the main shader program.
     *
     * @param texture  The texture to bind (base image)
     * @param alpha    Alpha multiplier for blending (0–1)
     */
    public void setRegularTextureMode(Texture texture, float alpha) {

        // Use the main shader program
        GLES20.glUseProgram(program);

        // Activate texture unit 0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        // Bind the texture (or 0 if null)
        int textureId = (texture != null) ? texture.glId : 0;
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);

        // Assign sampler uniform to texture unit 0
        GLES20.glUniform1i(uniformTextureBase, 0);

        // Mode 99 = regular texture mode (as in original GLSL)
        GLES20.glUniform1i(uniformMode, 99);

        // Set alpha (opacity)
        GLES20.glUniform1f(uniformAlpha, alpha);
    }

    /**
     * Enables grid rendering mode inside the point shader.
     *
     * @param scale Unused (kept for compatibility with old API).
     */
    public void setGrid(float scale) {

        // Activate the point shader
        GLES20.glUseProgram(programPoint);

        // Grid always uses full opacity
        GLES20.glUniform1f(uniformAlphaPoint, 1f);

        // Mode 99 = grid mode in point shader
        GLES20.glUniform1f(uniformModePoint, 99f);
    }

    /**
     * Sets the MVP matrix for point rendering (numbers shader).
     *
     * @param mvpMatrix A 4x4 matrix used for vertex transformation.
     */
    public void setUniformMvpPoint(float[] mvpMatrix) {

        // Ensure the point shader program is active
        GLES20.glUseProgram(programPoint);

        // Upload the MVP matrix to the shader
        GLES20.glUniformMatrix4fv(
                uniformMvpMatrixPoint,
                1,
                false,
                mvpMatrix,
                0
        );
    }

    /**
     * Binds the numbers texture and sets point rendering uniforms.
     *
     * @param texture  The number texture to use (0–9). If null, binds texture 0.
     * @param scale    Point size scale factor.
     */
    public void setNumbersTexture(Texture texture, float scale) {

        // Activate the correct shader program
        GLES20.glUseProgram(programPoint);

        // Set scale factor for point-size calculation
        GLES20.glUniform1f(uniformScaleFactor, scale);

        // Activate texture unit 0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        // Bind numbers texture (or 0 if null)
        int textureId = (texture != null) ? texture.glId : 0;
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);

        // The shader sampler uniform uses texture unit 0
        GLES20.glUniform1i(uniformTextureNumber, 0);

        // Mode = 0 → number rendering mode
        GLES20.glUniform1f(uniformModePoint, 0f);
    }

    /**
     * Sets the selected group UV coordinates for the shader.
     *
     * @param u group U coordinate (0–255)
     * @param v group V coordinate (0–255)
     */
    public void setSelectedGroup(int u, int v) {

        // Ensure the correct shader program is active
        GLES20.glUseProgram(program);

        // Normalize into [0..1] range (based on 256x256 lookup texture)
        float uNorm = u / 256f;
        float vNorm = v / 256f;

        GLES20.glUniform2f(uniformSelectedGroup, uNorm, vNorm);
    }

    public int getVertexPosAttrib() {
        return attribPosition;
    }

    public int getTexCoordAttrib() {
        return attribTexCoord;
    }

    /**
     * Loads and compiles a shader from assets.
     *
     * @param type    GLES20.GL_VERTEX_SHADER or GLES20.GL_FRAGMENT_SHADER
     * @param asset   Shader file inside /assets/
     * @param context App context used to read assets
     * @return shader ID, or 0 on failure
     */
    private static int loadShader(int type, String asset, Context context) {

        // Read shader source from asset file
        String shaderSource = readShaderCode(asset, context);
        if (shaderSource == null) {
            Log.e("Shader", "Failed to read shader asset: " + asset);
            return 0;
        }

        // Create shader
        int shaderId = GLES20.glCreateShader(type);
        if (shaderId == 0) {
            Log.e("Shader", "Failed to create shader: " + asset);
            return 0;
        }

        // Upload and compile shader
        GLES20.glShaderSource(shaderId, shaderSource);
        GLES20.glCompileShader(shaderId);

        // Check compile status
        int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shaderId, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

        if (compileStatus[0] == GLES20.GL_FALSE) {
            // Get the compilation log for debugging
            String log = GLES20.glGetShaderInfoLog(shaderId);
            Log.e("Shader", "Compilation failed for " + asset + ":\n" + log);

            GLES20.glDeleteShader(shaderId);
            return 0;
        }

        return shaderId;
    }

    public static String readShaderCode(String assetPath, Context context) {

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(assetPath))
        )) {

            StringBuilder sb = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }

            return sb.toString();

        } catch (IOException e) {
            return null;
        }
    }
}
