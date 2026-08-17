#version 460 core
#extension GL_ARB_gpu_shader_int64 : enable

#define QUAD_BUFFER_BINDING 1
#define MODEL_BUFFER_BINDING 3
#define MODEL_COLOUR_BUFFER_BINDING 4
#define POSITION_SCRATCH_BINDING 5
#define LIGHTING_SAMPLER_BINDING 1


#import <voxy:lod/quad_format.glsl>
#import <voxy:lod/block_model.glsl>
#import <voxy:lod/gl46/bindings.glsl>

//#define DEBUG_RENDER

layout(location = 0) out vec2 uv;
layout(location = 1) out flat uvec4 interData;

#ifdef PATCHED_SHADER
//Camera-relative, world-axis-aligned position of this vertex. A shader patch otherwise only receives uv and
//the packed per-quad data, which is not enough for anything view-dependent — fresnel, distance falloff,
//reflection vectors. Only emitted for patched shaders so the normal pipeline keeps its vertex layout.
layout(location = 2) out vec3 voxyRelativePos;
#endif

#ifdef DH_SHADER
//Pairs this vertex shader with the shaderpack's own (iris-transformed) dh_terrain/dh_water fragment shader,
//so LoD is lit by the pack itself rather than by voxy's approximation. The names and semantics below are
//dictated by that fragment and must match exactly; they were read off the transformed source rather than
//guessed. Deliberately declared without explicit locations so they link by name against a fragment we do
//not control.
//Explicit locations are required: this shader already binds uv at 0, interData at 1, voxyRelativePos at 2 and
//quadDebug at 7, and unqualified outputs would be auto-assigned from 0 and collide with them ("multiple
//bindings to output semantic ATTR0"). Starting at 8 clears all of them. The pack's fragment declares these
//without locations, which is fine — a location on only one side still matches by name.
layout(location = 8)  flat out int mat;
layout(location = 9)  out vec2 lmCoord;
layout(location = 10) flat out vec3 upVec;
layout(location = 11) flat out vec3 sunVec;
layout(location = 12) flat out vec3 northVec;
layout(location = 13) flat out vec3 eastVec;
layout(location = 14) out vec3 normal;
layout(location = 15) out vec3 playerPos;
layout(location = 16) out float iris_FogFragCoord;
//Injected by DhProgramSources in place of the pack's `in vec4 glColor`, because DH LoD is untextured and
//voxy's is not. The atlas lookup cannot be reduced to a per-vertex uv: merged quads span several tiles and
//repeat the texture, which quads.frag resolves with a per-pixel modf. So the raw uv and the per-quad tile
//data are handed over and the injected fragment code redoes that same lookup.
layout(location = 17) out vec4 voxy_vertexTint;
layout(location = 18) out vec2 voxy_uv;
layout(location = 19) flat out uvec3 voxy_texData;//x = modelId, y = face, z = flags (bit0 = alpha cutout)

uniform mat4 gbufferModelView;
uniform float sunPathRotation;
uniform float timeAngle;

//Copied verbatim from the pack's transformed dh_terrain vertex shader so the sun lands in the same place the
//fragment's lighting expects. Depends only on the two uniforms above plus gbufferModelView.
vec3 voxy_getSunVector() {
    const vec2 sunRotationData = vec2(cos(sunPathRotation * 0.01745329251994f), -sin(sunPathRotation * 0.01745329251994f));
    float ang = fract(timeAngle - 0.25f);
    ang = (ang + (cos(ang * 3.14159265358979f) * -0.5f + 0.5f - ang) / 3.0f) * 6.28318530717959f;
    return normalize((gbufferModelView * vec4(vec3(-sin(ang), cos(ang) * sunRotationData) * 2000.0f, 1.0f)).xyz);
}

//Face index layout matches the shading block below: high bits pick the axis (0 = Y, 1 = Z, 2 = X), low bit
//picks its sign, so face 0/1 are down/up.
vec3 voxy_faceNormal(uint face) {
    float facing = (face & 1u) == 1u ? 1.0 : -1.0;
    uint axis = face >> 1u;
    if (axis == 0u) return vec3(0.0, facing, 0.0);
    if (axis == 1u) return vec3(0.0, 0.0, facing);
    return vec3(facing, 0.0, 0.0);
}
#endif

uint packVec4(vec4 vec) {
    uvec4 vec_=uvec4(vec*255)<<uvec4(24,16,8,0);
    return vec_.x|vec_.y|vec_.z|vec_.w;
}

void setSizeAndFlags(uint modelId, uint _flags, ivec2 quadSize) {
    interData.x = (modelId<<16) | _flags | (uint(quadSize.x-1)<<8) | (uint(quadSize.y-1)<<12);
}

void setTintingAndExtra(vec4 _tinting, uint _conditionalTinting, uint addin) {
    interData.y = packVec4(_tinting);
    interData.z = _conditionalTinting;
    interData.w = addin;
}

#ifdef DEBUG_RENDER
layout(location = 7) out flat uint quadDebug;
#endif

/*
uint extractLodLevel() {
    return uint(gl_BaseInstance)>>27;
}

//Note the last 2 bits of gl_BaseInstance are unused
//Gives a relative position of +-255 relative to the player center in its respective lod
ivec3 extractRelativeLodPos() {
    return (ivec3(gl_BaseInstance)<<ivec3(5,14,23))>>ivec3(23);
}*/

vec4 getFaceSize(uint faceData) {
    float EPSILON = 0.00005f;

    vec4 faceOffsetsSizes = extractFaceSizes(faceData);

    //Expand the quads by a very small amount (because of the subtraction after this also becomes an implicit add)
    faceOffsetsSizes.xz -= vec2(EPSILON);

    //Make the end relative to the start
    faceOffsetsSizes.yw -= faceOffsetsSizes.xz;

    return faceOffsetsSizes;
}

vec3 swizzelDataAxis(uint axis, vec3 data) {
    return mix(mix(data.zxy,data.xzy,bvec3(axis==0)),data,bvec3(axis==1));
}

uint extractDetail(uvec2 encPos) {
    return encPos.x>>28;
}

ivec3 extractLoDPosition(uvec2 encPos) {
    int y = ((int(encPos.x)<<4)>>24);
    int x = (int(encPos.y)<<4)>>8;
    int z = int((encPos.x&((1u<<20)-1))<<4);
    z |= int(encPos.y>>28);
    z <<= 8;
    z >>= 8;
    return ivec3(x,y,z);
}


vec2 taaShift();

//TODO: add a mechanism so that some quads can ignore backface culling
// this would help alot with stuff like crops as they would look kinda weird i think,
// same with flowers etc
void main() {
    int cornerIdx = gl_VertexID&3;
    Quad quad = quadData[uint(gl_VertexID)>>2];
    uint face = extractFace(quad);
    uint modelId = extractStateId(quad);
    BlockModel model = modelData[modelId];
    uint faceData = model.faceData[face];
    bool isTranslucent = modelIsTranslucent(model);
    bool hasAO = modelHasMipmaps(model);//TODO: replace with per face AO flag
    bool isShaded = hasAO;//TODO: make this a per face flag


    uvec2 encPos = positionBuffer[gl_BaseInstance];
    uint lodLevel = extractDetail(encPos);

    ivec2 quadSize = extractSize(quad);




    vec4 faceSize = getFaceSize(faceData);

    vec2 cQuadSize = (faceSize.yw + quadSize - 1) * vec2((cornerIdx>>1)&1, cornerIdx&1);
    uv = faceSize.xz + cQuadSize;

    vec3 cornerPos = extractPos(quad);
    float depthOffset = extractFaceIndentation(faceData);
    cornerPos += swizzelDataAxis(face>>1, vec3(faceSize.xz, mix(depthOffset, 1-depthOffset, float(face&1u))));

    vec3 origin = vec3(((extractLoDPosition(encPos)<<lodLevel) - baseSectionPos)<<5);
    vec3 pointPos = (cornerPos+swizzelDataAxis(face>>1,vec3(cQuadSize,0)))*(1<<lodLevel)+origin;
    gl_Position = MVP*vec4(pointPos, 1.0);

    #ifdef PATCHED_SHADER
    //pointPos and cameraSubPos are both relative to baseSectionPos<<5, so the difference is the offset from
    //the eye — the same convention cmdgen.comp uses for its cornerPos - cameraSubPos. (MVP already has the
    //camera translation folded in, so gl_Position cannot be reused for this.)
    voxyRelativePos = pointPos - cameraSubPos;
    #endif

    #ifdef DH_SHADER
    {
        //playerPos in the pack's dh_terrain means the vertex relative to the eye in world-aligned space,
        //which is exactly pointPos - cameraSubPos; no matrix round-trip needed.
        playerPos = pointPos - cameraSubPos;

        //The pack derives these from gbufferModelView, so its normals and ours must share view space.
        upVec = normalize(gbufferModelView[1].xyz);
        eastVec = normalize(gbufferModelView[0].xyz);
        northVec = normalize(gbufferModelView[2].xyz);
        sunVec = voxy_getSunVector();
        normal = normalize(mat3(gbufferModelView) * voxy_faceNormal(face));

        //The pack's GetLightMapCoordinates() undoes the lightmap texel-centre inset, which collapses to
        //simply level/15 — the value voxy already carries. Light byte is (block << 4) | sky.
        uint dhLight = extractLightId(quad);
        lmCoord = clamp(vec2(float((dhLight >> 4) & 0xFu), float(dhLight & 0xFu)) / 15.0, 0.0, 1.0);

        //Only translucent geometry reaches the pack's dh_water program, and its entire water treatment is
        //gated on this id, so it has to be set or dh_water does nothing at all.
        mat = isTranslucent ? 12 : 0;//12 = DH_BLOCK_WATER

        iris_FogFragCoord = 0.0;

        voxy_uv = uv;
        //Same cutout derivation the normal path does below: the per-face flag, plus the override that only
        //applies once a quad has been merged past a single block. Without it the pack's fragment, which has
        //no cutout discard of its own (DH LoD is untextured so it never needed one), would render leaves and
        //grass as opaque squares.
        uint dhFlags = faceHasAlphaCuttout(faceData);
        dhFlags |= uint(any(greaterThan(quadSize, ivec2(1)))) & faceHasAlphaCuttoutOverride(faceData);
        voxy_texData = uvec3(modelId, face, dhFlags);

        //Biome/model tint. Alpha stays 1.0 because the pack reads glColor.a as vanilla AO, not opacity.
        vec3 dhTint = vec3(1.0);
        uint dhTintColour = model.colourTint;
        if (modelHasBiomeLUT(model)) {
            dhTintColour = colourData[dhTintColour + extractBiomeId(quad)];
        }
        if (dhTintColour != uint(-1)) {
            dhTint = vec3(uvec3(dhTintColour) >> uvec3(16, 8, 0) & uvec3(0xFFu)) / 255.0;
        }
        voxy_vertexTint = vec4(dhTint, 1.0);
    }
    #endif

    //Apply taa shift
    gl_Position.xy += taaShift()*gl_Position.w;



    if (cornerIdx == 1) //Only if we are the provoking vertex
    {
        //Generate tinting and flag data
        uint flags = faceHasAlphaCuttout(faceData);

        //We need to have a conditional override based on if the model size is < a full face + quadSize > 1
        flags |= uint(any(greaterThan(quadSize, ivec2(1)))) & faceHasAlphaCuttoutOverride(faceData);

        flags |= uint(!modelHasMipmaps(model))<<1;

        //Compute lighting
        uint lighting = extractLightId(quad);
        vec4 tinting = getLighting(extractLightId(quad));

        //Apply model colour tinting
        uint tintColour = model.colourTint;

        if (modelHasBiomeLUT(model)) {
            tintColour = colourData[tintColour + extractBiomeId(quad)];
        }

        uint tintState = faceTintState(faceData);

        uint conditionalTinting = 0;
        if (tintColour != uint(-1)) {
            flags |= tintState<<2;
            conditionalTinting = tintColour;
        }

        setSizeAndFlags(modelId, flags, quadSize);

        #ifndef PATCHED_SHADER
        uint addin = 0;
        if (!isTranslucent) {
            tinting.w = 0.0;
            //Encode the face, the lod level and
            uint encodedData = 0;
            encodedData |= face;
            encodedData |= (lodLevel<<3);
            encodedData |= uint(hasAO)<<6;
            addin = encodedData;
        }

        //Apply face tint
        if (isShaded) {
            //TODO: make branchless, infact apply ahead of time to the texture itself in ModelManager since that is
            // per face
            if ((face>>1) == 1) {//NORTH, SOUTH
                tinting.xyz *= 0.8f;
            } else if ((face>>1) == 2) {//EAST, WEST
                tinting.xyz *= 0.6f;
            } else if (face == 0) {//DOWN
                tinting.xyz *= 0.5f;
            }
        }

        setTintingAndExtra(tinting, conditionalTinting, addin|(face<<8));
        #else
        interData.y = lighting|(face<<8);
        interData.z = tintColour;
        #endif
    }


    #ifdef DEBUG_RENDER
    quadDebug = lodLevel;
    #endif
}

#ifndef TAA_PATCH
vec2 taaShift() {return vec2(0.0);}
#endif