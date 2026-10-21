attribute lowp vec4 vertex;
varying mediump vec2 texCoord;
varying mediump vec2 texCoordTransformed;
varying mediump vec3 outline2Coord;
uniform vec2 aspectRatio;
uniform mat4 viewMatrix;
uniform mat2 uvMatrix;
uniform vec4 outline2zoom;
void main ()
{
  lowp vec4 r;
  r.zw = vec2(0.0, 1.0);
  r.xy = (vertex.xy * aspectRatio);
  gl_Position = (r * viewMatrix);
  texCoord = ((vertex.xy * vec2(0.5, -0.5)) + vec2(0.5, 0.5));
  texCoordTransformed = (((vertex.xy * uvMatrix) * vec2(0.5, -0.5)) + vec2(0.5, 0.5));
  lowp vec3 y;
  y.xy = (((
    (vertex.xy * outline2zoom.z)
   * vec2(0.5, -0.5)) + vec2(0.5, 0.5)) + (outline2zoom.z * outline2zoom.xy));
  y.z = outline2zoom.w;
  outline2Coord = y;
}

