//import React from "./libs/react.js";
//import htm from "./libs/htm.module.js";
//const html = htm.bind(React.createElement);

function Menu(props) {
    return html`<aside id="menu">
        <span className="flex centeredV centeredH title">
            <svg version="1.1" id="Capa_1" xmlns="http://www.w3.org/2000/svg" x="0px" y="0px" viewBox="0 0 475.734 475.734"><path d="M464.776,210.981c-7.086-7.099-16.512-11.008-26.542-11.008h-19.12c-5.232-15.026-13.45-28.826-24.081-40.129 c-3.586-3.812-7.426-7.344-11.476-10.595c-0.079-0.064-0.158-0.129-0.239-0.19c-20.274-16.181-45.888-25.177-72.531-25.177 c-0.741,0-1.487,0.007-2.23,0.021c-16.181,0.288-31.244,2.76-45.694,7.578c-0.791,0.109-1.578,0.341-2.332,0.718 c-0.133,0.067-0.257,0.144-0.385,0.217c-11.869,4.222-23.341,10.051-34.699,17.586c-17.634-17.111-39.238-25.452-65.885-25.452 c-0.178,0-0.357,0-0.536,0.001c-24.189,0.109-47.125,7.982-65.957,21.565c-0.276,0.176-0.543,0.372-0.799,0.588 c-17.857,13.117-31.943,31.402-39.74,53.126c-0.111,0.309-0.205,0.621-0.308,0.931H37.5c-20.678,0-37.5,16.822-37.5,37.5 s16.822,37.5,37.5,37.5h14.854c7.96,22.365,22.568,41.467,42.245,55.244c19.48,13.638,42.293,20.846,65.972,20.846 c0.624,0,1.248,0,1.861-0.011c35.27-0.567,65.395-10.368,92.095-29.961c1.154-0.847,2.274-1.698,3.382-2.55 c16.412,17.358,41.213,29.037,62.734,29.037c37.143,0,73.333-22.728,92.2-57.903c2.653-4.945,4.898-10.132,6.738-15.491h18.654 c20.639,0,37.461-16.791,37.5-37.43C475.753,227.512,471.861,218.079,464.776,210.981z M96.471,162.426l16.042,16.041 c1.464,1.465,3.384,2.197,5.303,2.197s3.839-0.732,5.304-2.197c2.929-2.929,2.929-7.678-0.001-10.606l-14.231-14.23 c12.798-7.673,27.354-12.503,42.672-13.753v22.362c0,4.142,3.357,7.5,7.5,7.5s7.5-3.358,7.5-7.5v-22.431 c14.949,1.023,27.884,5.369,39.072,13.202l-14.851,14.85c-2.93,2.929-2.93,7.677-0.001,10.606c1.465,1.465,3.384,2.197,5.304,2.197 c1.919,0,3.839-0.732,5.303-2.197l15.643-15.642c0.251,0.26,0.51,0.504,0.759,0.768l0.166,0.171c0.095,0.094,0.183,0.19,0.26,0.272 c5.409,5.823,14.666,19.54,23.122,32.734l-20.391,13.533c-3.451,2.291-4.392,6.945-2.102,10.396 c1.444,2.175,3.827,3.354,6.256,3.354c1.425,0,2.865-0.405,4.141-1.252l20.101-13.341c2.926,4.706,5.486,8.894,7.391,12.044 c-10.332,7.284-19,15.402-26.691,22.635l-0.073,0.068c-2.037,1.916-4.021,3.773-5.99,5.588c-2.232-3.728-5.182-8.645-8.688-14.465 c-1.901-3.157-3.73-6.239-5.503-9.227c-15.878-26.764-28.42-47.903-50.588-47.903c-17.197,0-32.473,8.875-39.658,22.561H68.239 C74.465,185.605,84.283,172.548,96.471,162.426z M323.295,199.972c-19.494,0.091-37.214,4.558-53.836,13.581 c-0.755-1.257-1.593-2.642-2.502-4.134c9.541-6.712,22.011-13.385,43.244-13.385c7.045,0,13.559,1.383,19.102,3.938H323.295z M161.254,276.853c-3.335,0.044-6.664-0.363-9.903-1.159c23.881-0.879,41.154-11.23,57.413-22.109 c1.431,2.386,2.667,4.45,3.687,6.156C199.538,269.807,185.383,276.463,161.254,276.853z M147.851,200.762h-9.472 c4.9-4.401,11.942-7.561,20.822-7.561c3.13,0,6.073,0.907,8.919,2.576C161.262,200.274,155.476,200.722,147.851,200.762z M37.5,260.762c-12.406,0-22.5-10.093-22.5-22.5s10.094-22.5,22.5-22.5h0.895v26.39c0,4.142,3.357,7.5,7.5,7.5s7.5-3.358,7.5-7.5 v-26.39h33.406v26.39c0,4.142,3.357,7.5,7.5,7.5s7.5-3.358,7.5-7.5v-26.39h27.938h5.469v26.39c0,4.142,3.357,7.5,7.5,7.5 s7.5-3.358,7.5-7.5v-26.419c9.451-0.196,18.481-1.517,29.024-9.448c5.545,7.062,11.185,16.563,17.657,27.472 c1.342,2.262,2.729,4.592,4.142,6.953c-15.858,10.639-31.416,20.051-52.969,20.051h-19.718H49.187H37.5z M162.178,336.843 c-0.536,0.01-1.074,0.011-1.607,0.009c-20.585,0-40.423-6.271-57.368-18.134c-15.617-10.934-27.557-25.71-34.772-42.956h53.331 c0.749,0.743,1.521,1.455,2.306,2.151l-17.993,17.993c-2.929,2.929-2.929,7.678,0,10.606c1.465,1.464,3.385,2.197,5.304,2.197 s3.839-0.732,5.304-2.197l20.126-20.126c5.357,2.582,11.11,4.293,17.053,5.036v23.73c0,4.142,3.357,7.5,7.5,7.5s7.5-3.358,7.5-7.5 V291.53c8.55-0.6,15.964-1.978,22.501-3.918l14.635,22.614c1.436,2.218,3.845,3.426,6.304,3.426c1.396,0,2.809-0.39,4.068-1.205 c3.478-2.25,4.472-6.894,2.222-10.371l-13.01-20.103c7.389-3.743,13.516-8.214,19.098-12.813c1.582-1.303,3.138-2.65,4.688-4.017 l15.384,19.984c1.478,1.919,3.701,2.925,5.949,2.925c1.597,0,3.206-0.508,4.569-1.558c3.282-2.527,3.895-7.236,1.368-10.518 l-16.191-21.032c7.698-7.239,16.407-15.4,26.529-22.305l16.167,21.077c1.478,1.925,3.704,2.936,5.957,2.936 c1.592,0,3.197-0.505,4.559-1.55c3.287-2.521,3.907-7.229,1.387-10.516l-15.057-19.629c12.498-6.22,25.531-9.432,40.006-9.915 v27.109c0,4.142,3.357,7.5,7.5,7.5s7.5-3.358,7.5-7.5v-27.18h10.401c6.998,10.921,12.209,26.124,6.173,44.084 c-0.105,0.312-0.224,0.609-0.334,0.916h-27.748c-12.696,0.075-22.686,3.476-33.406,11.373c-5.858,4.318-11.789,9.792-18.94,16.506 c-7.301,6.873-15.575,14.663-25.487,21.934C221.21,327.722,194.688,336.32,162.178,336.843z M330.274,280.118 c-5.592,0.91-11.22-0.571-16.264-4.118c2.932-0.686,6.031-1.006,9.52-1.027h17.685C337.956,277.684,334.248,279.471,330.274,280.118 z M399.623,283.373c-16.29,30.369-47.292,49.993-78.981,49.993c-17.081,0-37.553-9.66-51.141-23.628 c4.286-3.782,8.224-7.484,11.914-10.958c6.776-6.362,12.372-11.533,17.564-15.36c0.119-0.088,0.234-0.166,0.352-0.252 c4.931,4.969,10.533,8.511,16.499,10.459v17.525c0,4.142,3.357,7.5,7.5,7.5s7.5-3.358,7.5-7.5v-15.981 c0.618-0.068,1.237-0.147,1.856-0.248c7.71-1.256,14.715-4.949,20.49-10.537l10.575,10.574c1.465,1.465,3.385,2.197,5.304,2.197 s3.839-0.732,5.304-2.197c2.929-2.929,2.929-7.678,0-10.606L362.205,272.2c1.377-2.615,2.587-5.402,3.582-8.365 c1.986-5.909,3.087-11.891,3.337-17.862h22.37c4.143,0,7.5-3.358,7.5-7.5s-3.357-7.5-7.5-7.5h-23.548 c-2.199-10.945-7.31-21.613-15.229-31.469c-9.43-11.738-24.927-18.47-42.517-18.47c-24.924,0-40.409,8.18-51.151,15.614 c-7.532-11.975-16.578-25.82-23.657-35.232c8.239-5.345,16.451-9.676,24.841-13.056l11.968,23.907 c1.315,2.626,3.963,4.144,6.713,4.144c1.128,0,2.273-0.255,3.352-0.795c3.704-1.854,5.203-6.36,3.35-10.064l-11.02-22.013 c10.819-2.921,22.097-4.422,34.236-4.638c1.44-0.026,2.876-0.015,4.31,0.019v26.433c0,4.142,3.357,7.5,7.5,7.5s7.5-3.358,7.5-7.5 v-24.988c14.185,2.424,27.627,7.786,39.39,15.701l-14.292,15.046c-2.854,3.003-2.731,7.75,0.272,10.603 c1.451,1.378,3.309,2.062,5.164,2.062c1.983,0,3.965-0.783,5.438-2.335l15.229-16.033c1.636,1.514,3.229,3.081,4.763,4.712 C412.249,200.04,418.774,247.67,399.623,283.373z M438.234,259.972h-14.68c2.355-12.238,2.73-25.013,0.967-37.661 c-0.343-2.464-0.783-4.909-1.282-7.339h11.955v27.18c0,4.142,3.357,7.5,7.5,7.5s7.5-3.358,7.5-7.5V218.43 c1.417,0.893,2.753,1.933,3.966,3.147c4.251,4.259,6.586,9.919,6.574,15.937C460.711,249.898,450.617,259.972,438.234,259.972z"></path><g></g><g></g><g></g><g></g><g></g><g></g><g></g><g></g><g></g><g></g><g></g><g></g><g></g><g></g><g></g></svg> 
            <i>Monitoraggio</i>
        </span>
        <span className="centeredV version">
            <span id="version">${props.version}</span>
            <span id="buildTime">${props.buildTime}</span>
            <span id="instance">${props.instance}</span>
        </span>
        <a className="flex centeredV ${props.page==''?'selected':''}" href="#/" title="Jobs">
            <i>Jobs</i>
            <svg aria-hidden="true" focusable="false" data-prefix="fas" data-icon="tasks" className="svg-inline--fa fa-tasks fa-w-16" role="img" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><path fill="currentColor" d="M139.61 35.5a12 12 0 0 0-17 0L58.93 98.81l-22.7-22.12a12 12 0 0 0-17 0L3.53 92.41a12 12 0 0 0 0 17l47.59 47.4a12.78 12.78 0 0 0 17.61 0l15.59-15.62L156.52 69a12.09 12.09 0 0 0 .09-17zm0 159.19a12 12 0 0 0-17 0l-63.68 63.72-22.7-22.1a12 12 0 0 0-17 0L3.53 252a12 12 0 0 0 0 17L51 316.5a12.77 12.77 0 0 0 17.6 0l15.7-15.69 72.2-72.22a12 12 0 0 0 .09-16.9zM64 368c-26.49 0-48.59 21.5-48.59 48S37.53 464 64 464a48 48 0 0 0 0-96zm432 16H208a16 16 0 0 0-16 16v32a16 16 0 0 0 16 16h288a16 16 0 0 0 16-16v-32a16 16 0 0 0-16-16zm0-320H208a16 16 0 0 0-16 16v32a16 16 0 0 0 16 16h288a16 16 0 0 0 16-16V80a16 16 0 0 0-16-16zm0 160H208a16 16 0 0 0-16 16v32a16 16 0 0 0 16 16h288a16 16 0 0 0 16-16v-32a16 16 0 0 0-16-16z"></path></svg>
        </a>
        <a className="flex centeredV ${props.page=='actions'?'selected':''}" href="#/actions" title="Actions">
            <i>Actions</i>
            <svg aria-hidden="true" focusable="false" data-prefix="far" data-icon="hand-pointer" className="svg-inline--fa fa-hand-pointer fa-w-14" role="img" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 448 512"><path fill="currentColor" d="M358.182 179.361c-19.493-24.768-52.679-31.945-79.872-19.098-15.127-15.687-36.182-22.487-56.595-19.629V67c0-36.944-29.736-67-66.286-67S89.143 30.056 89.143 67v161.129c-19.909-7.41-43.272-5.094-62.083 8.872-29.355 21.795-35.793 63.333-14.55 93.152l109.699 154.001C134.632 501.59 154.741 512 176 512h178.286c30.802 0 57.574-21.5 64.557-51.797l27.429-118.999A67.873 67.873 0 0 0 448 326v-84c0-46.844-46.625-79.273-89.818-62.639zM80.985 279.697l27.126 38.079c8.995 12.626 29.031 6.287 29.031-9.283V67c0-25.12 36.571-25.16 36.571 0v175c0 8.836 7.163 16 16 16h6.857c8.837 0 16-7.164 16-16v-35c0-25.12 36.571-25.16 36.571 0v35c0 8.836 7.163 16 16 16H272c8.837 0 16-7.164 16-16v-21c0-25.12 36.571-25.16 36.571 0v21c0 8.836 7.163 16 16 16h6.857c8.837 0 16-7.164 16-16 0-25.121 36.571-25.16 36.571 0v84c0 1.488-.169 2.977-.502 4.423l-27.43 119.001c-1.978 8.582-9.29 14.576-17.782 14.576H176c-5.769 0-11.263-2.878-14.697-7.697l-109.712-154c-14.406-20.223 14.994-42.818 29.394-22.606zM176.143 400v-96c0-8.837 6.268-16 14-16h6c7.732 0 14 7.163 14 16v96c0 8.837-6.268 16-14 16h-6c-7.733 0-14-7.163-14-16zm75.428 0v-96c0-8.837 6.268-16 14-16h6c7.732 0 14 7.163 14 16v96c0 8.837-6.268 16-14 16h-6c-7.732 0-14-7.163-14-16zM327 400v-96c0-8.837 6.268-16 14-16h6c7.732 0 14 7.163 14 16v96c0 8.837-6.268 16-14 16h-6c-7.732 0-14-7.163-14-16z"></path></svg>
        </a>
        <a className="flex centeredV ${props.page=='configuration'?'selected':''}" href="#/configuration" title="Configuration">
        <i>Configuration</i>
            <svg aria-hidden="true" focusable="false" data-prefix="fas" data-icon="cogs" className="svg-inline--fa fa-cogs fa-w-20" role="img" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 640 512"><path fill="currentColor" d="M512.1 191l-8.2 14.3c-3 5.3-9.4 7.5-15.1 5.4-11.8-4.4-22.6-10.7-32.1-18.6-4.6-3.8-5.8-10.5-2.8-15.7l8.2-14.3c-6.9-8-12.3-17.3-15.9-27.4h-16.5c-6 0-11.2-4.3-12.2-10.3-2-12-2.1-24.6 0-37.1 1-6 6.2-10.4 12.2-10.4h16.5c3.6-10.1 9-19.4 15.9-27.4l-8.2-14.3c-3-5.2-1.9-11.9 2.8-15.7 9.5-7.9 20.4-14.2 32.1-18.6 5.7-2.1 12.1.1 15.1 5.4l8.2 14.3c10.5-1.9 21.2-1.9 31.7 0L552 6.3c3-5.3 9.4-7.5 15.1-5.4 11.8 4.4 22.6 10.7 32.1 18.6 4.6 3.8 5.8 10.5 2.8 15.7l-8.2 14.3c6.9 8 12.3 17.3 15.9 27.4h16.5c6 0 11.2 4.3 12.2 10.3 2 12 2.1 24.6 0 37.1-1 6-6.2 10.4-12.2 10.4h-16.5c-3.6 10.1-9 19.4-15.9 27.4l8.2 14.3c3 5.2 1.9 11.9-2.8 15.7-9.5 7.9-20.4 14.2-32.1 18.6-5.7 2.1-12.1-.1-15.1-5.4l-8.2-14.3c-10.4 1.9-21.2 1.9-31.7 0zm-10.5-58.8c38.5 29.6 82.4-14.3 52.8-52.8-38.5-29.7-82.4 14.3-52.8 52.8zM386.3 286.1l33.7 16.8c10.1 5.8 14.5 18.1 10.5 29.1-8.9 24.2-26.4 46.4-42.6 65.8-7.4 8.9-20.2 11.1-30.3 5.3l-29.1-16.8c-16 13.7-34.6 24.6-54.9 31.7v33.6c0 11.6-8.3 21.6-19.7 23.6-24.6 4.2-50.4 4.4-75.9 0-11.5-2-20-11.9-20-23.6V418c-20.3-7.2-38.9-18-54.9-31.7L74 403c-10 5.8-22.9 3.6-30.3-5.3-16.2-19.4-33.3-41.6-42.2-65.7-4-10.9.4-23.2 10.5-29.1l33.3-16.8c-3.9-20.9-3.9-42.4 0-63.4L12 205.8c-10.1-5.8-14.6-18.1-10.5-29 8.9-24.2 26-46.4 42.2-65.8 7.4-8.9 20.2-11.1 30.3-5.3l29.1 16.8c16-13.7 34.6-24.6 54.9-31.7V57.1c0-11.5 8.2-21.5 19.6-23.5 24.6-4.2 50.5-4.4 76-.1 11.5 2 20 11.9 20 23.6v33.6c20.3 7.2 38.9 18 54.9 31.7l29.1-16.8c10-5.8 22.9-3.6 30.3 5.3 16.2 19.4 33.2 41.6 42.1 65.8 4 10.9.1 23.2-10 29.1l-33.7 16.8c3.9 21 3.9 42.5 0 63.5zm-117.6 21.1c59.2-77-28.7-164.9-105.7-105.7-59.2 77 28.7 164.9 105.7 105.7zm243.4 182.7l-8.2 14.3c-3 5.3-9.4 7.5-15.1 5.4-11.8-4.4-22.6-10.7-32.1-18.6-4.6-3.8-5.8-10.5-2.8-15.7l8.2-14.3c-6.9-8-12.3-17.3-15.9-27.4h-16.5c-6 0-11.2-4.3-12.2-10.3-2-12-2.1-24.6 0-37.1 1-6 6.2-10.4 12.2-10.4h16.5c3.6-10.1 9-19.4 15.9-27.4l-8.2-14.3c-3-5.2-1.9-11.9 2.8-15.7 9.5-7.9 20.4-14.2 32.1-18.6 5.7-2.1 12.1.1 15.1 5.4l8.2 14.3c10.5-1.9 21.2-1.9 31.7 0l8.2-14.3c3-5.3 9.4-7.5 15.1-5.4 11.8 4.4 22.6 10.7 32.1 18.6 4.6 3.8 5.8 10.5 2.8 15.7l-8.2 14.3c6.9 8 12.3 17.3 15.9 27.4h16.5c6 0 11.2 4.3 12.2 10.3 2 12 2.1 24.6 0 37.1-1 6-6.2 10.4-12.2 10.4h-16.5c-3.6 10.1-9 19.4-15.9 27.4l8.2 14.3c3 5.2 1.9 11.9-2.8 15.7-9.5 7.9-20.4 14.2-32.1 18.6-5.7 2.1-12.1-.1-15.1-5.4l-8.2-14.3c-10.4 1.9-21.2 1.9-31.7 0zM501.6 431c38.5 29.6 82.4-14.3 52.8-52.8-38.5-29.6-82.4 14.3-52.8 52.8z"></path></svg>
        </a>
        <a className="flex centeredV ${props.page=='rendicontazioni'?'selected':''}" href="#/rendicontazioni" title="Rendi Bollo">
            <i>Rendi Bollo</i>
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 384 512"><!--! Font Awesome Pro 6.2.1 by @fontawesome - https://fontawesome.com License - https://fontawesome.com/license (Commercial License) Copyright 2022 Fonticons, Inc. --><path d="M64 0C28.7 0 0 28.7 0 64V448c0 35.3 28.7 64 64 64H320c35.3 0 64-28.7 64-64V64c0-35.3-28.7-64-64-64H64zM96 64H288c17.7 0 32 14.3 32 32v32c0 17.7-14.3 32-32 32H96c-17.7 0-32-14.3-32-32V96c0-17.7 14.3-32 32-32zM64 224c0-17.7 14.3-32 32-32s32 14.3 32 32s-14.3 32-32 32s-32-14.3-32-32zm32 64c17.7 0 32 14.3 32 32s-14.3 32-32 32s-32-14.3-32-32s14.3-32 32-32zM64 416c0-17.7 14.3-32 32-32h96c17.7 0 32 14.3 32 32s-14.3 32-32 32H96c-17.7 0-32-14.3-32-32zM192 192c17.7 0 32 14.3 32 32s-14.3 32-32 32s-32-14.3-32-32s14.3-32 32-32zM160 320c0-17.7 14.3-32 32-32s32 14.3 32 32s-14.3 32-32 32s-32-14.3-32-32zM288 192c17.7 0 32 14.3 32 32s-14.3 32-32 32s-32-14.3-32-32s14.3-32 32-32zM256 320c0-17.7 14.3-32 32-32s32 14.3 32 32s-14.3 32-32 32s-32-14.3-32-32zm32 64c17.7 0 32 14.3 32 32s-14.3 32-32 32s-32-14.3-32-32s14.3-32 32-32z"/></svg>
        </a>
        <a className="flex centeredV ${props.page=='changelogs'?'selected':''}" href="#/changelogs" title="Changelogs">
            <i>Changelogs</i>
            <svg  aria-hidden="true" focusable="false" data-prefix="fas" data-icon="cogs" className="svg-inline--fa fa-cogs fa-w-20" role="img" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 576 512"><path d="M549.8 237.5c-31.23-5.719-46.84-20.06-47.13-20.31C490.4 205 470.3 205.1 457.7 216.8c-1 .9375-25.14 23-73.73 23s-72.73-22.06-73.38-22.62C298.4 204.9 278.3 205.1 265.7 216.8c-1 .9375-25.14 23-73.73 23S119.3 217.8 118.6 217.2C106.4 204.9 86.35 205 73.74 216.9C73.09 217.4 57.48 231.8 26.24 237.5c-17.38 3.188-28.89 19.84-25.72 37.22c3.188 17.38 19.78 29.09 37.25 25.72C63.1 295.8 82.49 287.1 95.96 279.2c19.5 11.53 51.47 24.68 96.04 24.68c44.55 0 76.49-13.12 96-24.65c19.52 11.53 51.45 24.59 96 24.59c44.58 0 76.55-13.09 96.05-24.62c13.47 7.938 32.86 16.62 58.19 21.25c17.56 3.375 34.06-8.344 37.25-25.72C578.7 257.4 567.2 240.7 549.8 237.5zM549.8 381.7c-31.23-5.719-46.84-20.06-47.13-20.31c-12.22-12.19-32.31-12.12-44.91-.375C456.7 361.9 432.6 384 384 384s-72.73-22.06-73.38-22.62c-12.22-12.25-32.3-12.12-44.89-.375C264.7 361.9 240.6 384 192 384s-72.73-22.06-73.38-22.62c-12.22-12.25-32.28-12.16-44.89-.3438c-.6562 .5938-16.27 14.94-47.5 20.66c-17.38 3.188-28.89 19.84-25.72 37.22C3.713 436.3 20.31 448 37.78 444.6C63.1 440 82.49 431.3 95.96 423.4c19.5 11.53 51.51 24.62 96.08 24.62c44.55 0 76.45-13.06 95.96-24.59C307.5 434.9 339.5 448 384.1 448c44.58 0 76.5-13.09 95.1-24.62c13.47 7.938 32.86 16.62 58.19 21.25C555.8 448 572.3 436.3 575.5 418.9C578.7 401.5 567.2 384.9 549.8 381.7zM37.78 156.4c25.33-4.625 44.72-13.31 58.19-21.25c19.5 11.53 51.47 24.68 96.04 24.68c44.55 0 76.49-13.12 96-24.65c19.52 11.53 51.45 24.59 96 24.59c44.58 0 76.55-13.09 96.05-24.62c13.47 7.938 32.86 16.62 58.19 21.25c17.56 3.375 34.06-8.344 37.25-25.72c3.172-17.38-8.344-34.03-25.72-37.22c-31.23-5.719-46.84-20.06-47.13-20.31c-12.22-12.19-32.31-12.12-44.91-.375c-1 .9375-25.14 23-73.73 23s-72.73-22.06-73.38-22.62c-12.22-12.25-32.3-12.12-44.89-.375c-1 .9375-25.14 23-73.73 23S119.3 73.76 118.6 73.2C106.4 60.95 86.35 61.04 73.74 72.85C73.09 73.45 57.48 87.79 26.24 93.51c-17.38 3.188-28.89 19.84-25.72 37.22C3.713 148.1 20.31 159.8 37.78 156.4z"/></svg>
        </a>
    </aside>`;
  }

//export default Menu